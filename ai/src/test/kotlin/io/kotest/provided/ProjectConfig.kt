package io.kotest.provided

import com.wingedsheep.engine.support.TestHangGuard
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import kotlin.time.Duration

/**
 * Project-wide Kotest config for `:ai` tests.
 *
 * Installs the shared anti-hang guard ([TestHangGuard]) so a runaway test (e.g. an AI search or
 * simulation that loops) fails fast instead of pinning a core and hanging the test JVM for hours.
 * Disabled benchmarks (run with `-Dbenchmark=true`) opt out of the guard automatically.
 */
class ProjectConfig : AbstractProjectConfig() {
    override val timeout: Duration = TestHangGuard.defaultTestTimeout
    override val extensions: List<Extension> = TestHangGuard.extensions()
}
