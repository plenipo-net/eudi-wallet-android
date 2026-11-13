/*
 * Copyright (c) 2026 Plenipo contributors
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.corelogic.extension

import eu.europa.ec.corelogic.model.PlenipoLegalPrincipalDomain
import eu.europa.ec.corelogic.model.PlenipoLimitsDomain
import eu.europa.ec.corelogic.model.PresentationTransactionDataDomain
import eu.europa.ec.eudi.wallet.transfer.openId4vp.TransactionDataType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** The transaction data type of a Plenipo mandate (https://plenipo.net). */
const val PLENIPO_MANDATE_TYPE = "plenipo_mandate"

/** Declared in the OpenID4VP configuration: the payload is read as JSON and mapped below. */
val PLENIPO_MANDATE_TRANSACTION_DATA: TransactionDataType = TransactionDataType.raw(PLENIPO_MANDATE_TYPE)

private val MANDATE_MEMBERS =
    setOf("v", "id", "agent", "actions", "counterparties", "limits", "nbf", "exp", "delegation", "purpose", "status", "principal")
private val TRANSACTION_MEMBERS = setOf("type", "credential_ids", "transaction_data_hashes_alg", "mandate")

/**
 * Maps a `plenipo_mandate` transaction data object, or returns null when [payload] is not one. A
 * mandate that is malformed or carries a member this build does not show maps to
 * [PresentationTransactionDataDomain.Unavailable], so it is declined rather than shown in part.
 */
internal fun plenipoMandateDomainOrNull(payload: JsonObject): PresentationTransactionDataDomain? {
    if (payload.string("type") != PLENIPO_MANDATE_TYPE) return null
    return runCatching { payload.toPlenipoMandate() }.getOrDefault(PresentationTransactionDataDomain.Unavailable)
}

private fun JsonObject.toPlenipoMandate(): PresentationTransactionDataDomain.PlenipoMandate {
    require(keys.all { it in TRANSACTION_MEMBERS })
    val mandate = this["mandate"] as JsonObject
    require(mandate.keys.all { it in MANDATE_MEMBERS })
    require(mandate.long("v") == 1L)
    val actions = mandate.strings("actions")
    require(actions.isNotEmpty())
    val limits = (mandate["limits"] as JsonObject?)?.let { l ->
        require(l.keys.all { it in setOf("currency", "per_operation", "total") })
        val currency = l.string("currency")
        require(currency != null && Regex("^[A-Z]{3}$").matches(currency))
        PlenipoLimitsDomain(
            currency = currency,
            perOperation = l["per_operation"]?.let { (it as JsonPrimitive).nonNegativeLong() },
            total = l["total"]?.let { (it as JsonPrimitive).nonNegativeLong() },
        )
    }
    val depth = (mandate["delegation"] as JsonObject?)?.let { d ->
        require(d.keys == setOf("max_depth"))
        (d["max_depth"] as JsonPrimitive).nonNegativeLong().toInt()
    }
    val principal = (mandate["principal"] as JsonObject?)?.let { p ->
        require(p.keys.all { it in setOf("type", "euid", "role") } && p.string("type") == "legal_person")
        PlenipoLegalPrincipalDomain(euid = p.string("euid")!!, role = p.string("role"))
    }
    return PresentationTransactionDataDomain.PlenipoMandate(
        mandateId = mandate.string("id")!!,
        purpose = mandate.string("purpose"),
        actions = actions,
        counterparties = if (mandate.containsKey("counterparties")) mandate.strings("counterparties").also { require(it.isNotEmpty()) } else emptyList(),
        limits = limits,
        notBefore = mandate.long("nbf")!!,
        expiresAt = mandate.long("exp")!!.also { require(it > mandate.long("nbf")!!) },
        maxDelegationDepth = depth,
        legalPrincipal = principal,
        transactionDataHashAlgorithms = if (containsKey("transaction_data_hashes_alg")) strings("transaction_data_hashes_alg") else emptyList(),
    )
}

private fun JsonObject.string(name: String): String? = (this[name] as JsonPrimitive?)?.let { p ->
    require(p.isString)
    p.content
}

private fun JsonObject.long(name: String): Long? = (this[name] as JsonPrimitive?)?.let { p ->
    require(!p.isString && p.booleanOrNull == null)
    p.longOrNull ?: throw IllegalArgumentException("$name is not an integer")
}

private fun JsonPrimitive.nonNegativeLong(): Long {
    require(!isString && booleanOrNull == null)
    return longOrNull?.takeIf { it >= 0 } ?: throw IllegalArgumentException("not a non-negative integer")
}

private fun JsonObject.strings(name: String): List<String> =
    (this[name] as JsonArray).map { element: JsonElement ->
        val p = element as JsonPrimitive
        require(p.isString)
        p.content
    }
