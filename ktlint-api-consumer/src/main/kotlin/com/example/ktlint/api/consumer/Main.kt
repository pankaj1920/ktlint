import com.example.ktlint.api.consumer.KtlintApiConsumer
import com.pinterest.ktlint.logger.api.initKtLintKLogger
import mu.KotlinLogging
import kotlin.system.exitProcess

private val LOGGER = KotlinLogging.logger {}.initKtLintKLogger()

public fun main(args: Array<String>) {
    if (args.size != 2) {
        LOGGER.error { "Expected two arguments" }
        exitProcess(1)
    }

    KtlintApiConsumer().run(args[0], args[1])
}
