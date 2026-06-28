package com.wingedsheep.gameserver.auth

import com.wingedsheep.gameserver.config.AdminProperties
import com.wingedsheep.gameserver.config.GameProperties
import com.wingedsheep.gameserver.persistence.UserRepository
import com.wingedsheep.gameserver.persistence.UserRow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.Optional

/**
 * The two ways into the admin dashboard, and the rejections in between. The bootstrap password and the
 * admin-account token are independent paths; either suffices, neither leaks the other's absence.
 */
class AdminAuthServiceTest : FunSpec({

    fun service(password: String = "secret"): AdminAuthService =
        AdminAuthService(GameProperties(admin = AdminProperties(password = password)))

    fun claims(uid: Long) = AuthClaims(uid = uid, email = "u$uid@test", exp = Long.MAX_VALUE)

    fun adminUser(uid: Long, isAdmin: Boolean) =
        Optional.of(UserRow(id = uid, email = "u$uid@test", displayName = "u$uid", isAdmin = isAdmin))

    // ---- Password (bootstrap) path ----

    test("correct bootstrap password authorizes") {
        service().isAuthorized(password = "secret", authorization = null) shouldBe true
    }

    test("wrong bootstrap password is rejected") {
        service().isAuthorized(password = "nope", authorization = null) shouldBe false
    }

    test("a blank configured password never matches, even against blank/null input") {
        service(password = "").isAuthorized(password = "", authorization = null) shouldBe false
        service(password = "").isAuthorized(password = null, authorization = null) shouldBe false
    }

    // ---- Admin-account token path ----

    test("an admin account's token authorizes") {
        val svc = service().apply {
            authSupport = mockk { every { userOrNull("Bearer t") } returns claims(5) }
            users = mockk { every { findById(5) } returns adminUser(5, isAdmin = true) }
        }
        svc.isAuthorized(password = null, authorization = "Bearer t") shouldBe true
    }

    test("a non-admin account's token is rejected") {
        val svc = service().apply {
            authSupport = mockk { every { userOrNull(any()) } returns claims(6) }
            users = mockk { every { findById(6) } returns adminUser(6, isAdmin = false) }
        }
        svc.isAuthorized(password = null, authorization = "Bearer t") shouldBe false
    }

    test("an invalid token is rejected") {
        val svc = service().apply { authSupport = mockk { every { userOrNull(any()) } returns null } }
        svc.isAuthorized(password = null, authorization = "Bearer bad") shouldBe false
    }

    test("when accounts are disabled (no beans) only the password path exists") {
        val svc = service() // authSupport / users left null, as on an accounts-disabled server
        svc.isAuthorized(password = "secret", authorization = "Bearer anything") shouldBe true
        svc.isAuthorized(password = "wrong", authorization = "Bearer anything") shouldBe false
    }

    // ---- Feature availability ----

    test("isConfigured is false with no password and accounts disabled") {
        service(password = "").isConfigured() shouldBe false
    }

    test("isConfigured is true when accounts are enabled even without a password") {
        service(password = "").apply { authSupport = mockk() }.isConfigured() shouldBe true
    }
})
