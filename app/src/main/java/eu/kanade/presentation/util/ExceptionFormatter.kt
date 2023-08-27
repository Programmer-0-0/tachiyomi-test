package eu.kanade.presentation.util

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.online.LicensedMangaChaptersException
import eu.kanade.tachiyomi.util.system.isOnline
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.source.model.SourceNotInstalledException
import java.net.UnknownHostException

context(Context)
val Throwable.formattedMessage: String
    get() {
        when (this) {
            is HttpException -> return getString(R.string.exception_http, code)
            is UnknownHostException -> {
                return if (!isOnline()) {
                    getString(R.string.exception_offline)
                } else {
                    getString(R.string.exception_unknown_host, message)
                }
            }

            is NoResultsException -> return getString(R.string.no_results_found)
            is SourceNotInstalledException -> return getString(R.string.loader_not_implemented_error)
            is LicensedMangaChaptersException -> return getString(R.string.licensed_manga_chapters_error)
        }
        return when (val className = this::class.simpleName) {
            "Exception" -> message ?: className
            // General networking exceptions
            "SocketException" -> getString(R.string.exception_socket_error)
            "ConnectException" -> getString(R.string.exception_connect)
            "BindException" -> getString(R.string.exception_bind_port)
            "InterruptedIOException" -> getString(R.string.exception_io_interrupted)
            "HttpRetryException" -> getString(R.string.exception_http_retry)
            "PortUnreachableException" -> getString(R.string.exception_port_unreachable)
            // General IO-related exceptions
            "IOException" -> if (isOnline()) getString(R.string.exception_io_error) else getString(R.string.exception_offline)
            "TimeoutException" -> getString(R.string.exception_timed_out)
            // SSL & Security-related
            "SSLException" -> getString(R.string.exception_ssl_connection)
            "CertificateExpiredException" -> getString(R.string.exception_ssl_certificate)
            "CertificateNotYetValidException" -> getString(R.string.exception_ssl_not_valid)
            "CertificateParsingException" -> getString(R.string.exception_ssl_parsing)
            "CertificateEncodingException" -> getString(R.string.exception_ssl_encoding)
            "UnrecoverableKeyException" -> getString(R.string.exception_unrecoverable_key)
            "KeyManagementException" -> getString(R.string.exception_key_management)
            "NoSuchAlgorithmException" -> getString(R.string.exception_algorithm)
            "KeyStoreException" -> getString(R.string.exception_keystore)
            "NoSuchProviderException" -> getString(R.string.exception_security_provider)
            "SignatureException" -> getString(R.string.exception_signature_validation)
            "InvalidKeySpecException" -> getString(R.string.exception_key_specification)
            // Host & DNS-related
            "NoRouteToHostException" -> getString(R.string.exception_route_to_host)
            // URL & URI related
            "URISyntaxException" -> getString(R.string.exception_uri_syntax)
            "MalformedURLException" -> getString(R.string.exception_malformed_url)
            // Authentication & Proxy
            "ProtocolException" -> getString(R.string.exception_protocol_proxy_type)
            // Concurrency & Operation-related
            "CancellationException" -> getString(R.string.exception_cancelled)
            "InterruptedException" -> getString(R.string.exception_interrupted)
            "IllegalStateException" -> getString(R.string.exception_unexpected_state)
            "UnsupportedOperationException" -> getString(R.string.exception_not_supported)
            "IllegalArgumentException" -> getString(R.string.exception_invalid_argument)

            else -> "$className: $message"
        }
    }
