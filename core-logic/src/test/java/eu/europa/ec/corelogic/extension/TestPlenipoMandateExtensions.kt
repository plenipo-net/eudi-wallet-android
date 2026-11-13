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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestPlenipoMandateExtensions {

    private fun payload(mandate: String, extra: String = ""): JsonObject = Json.parseToJsonElement(
        """{"type":"plenipo_mandate","credential_ids":["pid"],"transaction_data_hashes_alg":["sha-256"]$extra,"mandate":$mandate}"""
    ).jsonObject

    private val fullMandate = """{
        "v":1,"id":"6f1c","agent":{"jwk":{"kty":"EC","crv":"P-256","x":"a","y":"b"}},
        "actions":["payments.transfer","accounts.read"],"counterparties":["lumen-energia.example"],
        "limits":{"currency":"EUR","per_operation":15000,"total":40000},
        "nbf":1763000000,"exp":1765592000,"delegation":{"max_depth":1},
        "purpose":"Pay the electricity bill","status":{"status_list":{"uri":"https://p.example/s","idx":3}},
        "principal":{"type":"legal_person","euid":"IT.CCIAA.RM123","role":"director"}
    }"""

    @Test
    fun `maps every field of a mandate`() {
        assertEquals(
            PresentationTransactionDataDomain.PlenipoMandate(
                mandateId = "6f1c",
                purpose = "Pay the electricity bill",
                actions = listOf("payments.transfer", "accounts.read"),
                counterparties = listOf("lumen-energia.example"),
                limits = PlenipoLimitsDomain(currency = "EUR", perOperation = 15000, total = 40000),
                notBefore = 1763000000,
                expiresAt = 1765592000,
                maxDelegationDepth = 1,
                legalPrincipal = PlenipoLegalPrincipalDomain(euid = "IT.CCIAA.RM123", role = "director"),
                transactionDataHashAlgorithms = listOf("sha-256"),
            ),
            plenipoMandateDomainOrNull(payload(fullMandate)),
        )
    }

    @Test
    fun `a mandate without counterparties, limits or delegation allows any counterparty and no delegation`() {
        val mapped = plenipoMandateDomainOrNull(
            payload("""{"v":1,"id":"x","agent":{"jwk":{}},"actions":["*"],"nbf":1,"exp":2}""")
        ) as PresentationTransactionDataDomain.PlenipoMandate
        assertEquals(emptyList<String>(), mapped.counterparties)
        assertNull(mapped.limits)
        assertNull(mapped.maxDelegationDepth)
        assertNull(mapped.purpose)
    }

    @Test
    fun `other transaction data types are not Plenipo mandates`() {
        assertNull(plenipoMandateDomainOrNull(Json.parseToJsonElement("""{"type":"qes_approval"}""").jsonObject))
    }

    @Test
    fun `an unknown member, a wrong type or a broken value is declined`() {
        val unavailable = PresentationTransactionDataDomain.Unavailable
        val base = """"v":1,"id":"x","agent":{"jwk":{}},"actions":["a"],"nbf":1,"exp":2"""
        // A member this build cannot show could restrict or widen the mandate without the person seeing it.
        assertEquals(unavailable, plenipoMandateDomainOrNull(payload("{$base,\"scope_extra\":true}")))
        assertEquals(unavailable, plenipoMandateDomainOrNull(payload("{$base}", extra = ""","note":"x"""")))
        assertEquals(unavailable, plenipoMandateDomainOrNull(payload("{$base,\"limits\":{\"currency\":\"EUR\",\"daily\":5}}")))
        assertEquals(unavailable, plenipoMandateDomainOrNull(payload("{$base,\"limits\":{\"currency\":\"EUR\",\"total\":\"5\"}}")))
        assertEquals(unavailable, plenipoMandateDomainOrNull(payload("{$base,\"limits\":{\"currency\":\"EUR\",\"total\":-5}}")))
        assertEquals(unavailable, plenipoMandateDomainOrNull(payload("{$base,\"counterparties\":[]}")))
        assertEquals(unavailable, plenipoMandateDomainOrNull(payload("""{"v":2,"id":"x","agent":{},"actions":["a"],"nbf":1,"exp":2}""")))
        assertEquals(unavailable, plenipoMandateDomainOrNull(payload("""{"v":1,"id":"x","agent":{},"actions":[],"nbf":1,"exp":2}""")))
        assertEquals(unavailable, plenipoMandateDomainOrNull(payload("""{"v":1,"id":"x","agent":{},"actions":["a"],"nbf":5,"exp":2}""")))
        assertEquals(unavailable, plenipoMandateDomainOrNull(payload("""{"v":1,"id":"x","agent":{},"actions":["a"],"nbf":1.5,"exp":2}""")))
    }
}
