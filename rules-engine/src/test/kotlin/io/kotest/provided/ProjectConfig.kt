package io.kotest.provided

import com.wingedsheep.engine.support.TestHangGuard
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import kotlin.time.Duration

/**
 * Project-wide Kotest config for `:rules-engine` tests.
 *
 * Installs the shared anti-hang guard ([TestHangGuard]) so a runaway test (e.g. a new effect or
 * card that loops) fails fast instead of pinning a core and hanging the test JVM for hours.
 */
class ProjectConfig : AbstractProjectConfig() {
    override val timeout: Duration = TestHangGuard.defaultTestTimeout
    override val extensions: List<Extension> = TestHangGuard.extensions()
}
