package io.github.alelk.tgvd.server.infra.config

data class AppConfig(
    val server: ServerConfig,
    val telegram: TelegramConfig,
    val db: DbConfig,
    val storage: StorageConfig,
    val ytDlp: YtDlpConfig = YtDlpConfig(),
    val ffmpeg: FfmpegConfig = FfmpegConfig(),
    val postProcess: PostProcessConfig = PostProcessConfig(),
    val jobs: JobsConfig = JobsConfig(),
    val logging: LoggingConfig = LoggingConfig(),
    val llm: LlmConfig = LlmConfig(),
    val proxy: ProxyConfig = ProxyConfig(),
    val cors: CorsConfig = CorsConfig(),
)
