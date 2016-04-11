import org.apache.commons.io.IOUtils
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.StringReader
import java.security.InvalidParameterException
import java.util.*
import javax.xml.stream.XMLInputFactory

private class EnaCredentials {
    companion object {
        private fun exception(name: String) = InvalidParameterException("Environment variable '$name' not set.")
        val user = System.getenv("ena_user") ?: throw exception("ena_user")
        val password = System.getenv("ena_password") ?: throw exception("ena_password")
    }
}

private class EnaUrls {
    companion object {
        val testServer: String
        val productionServer: String
        val ftpServer: String

        init {
            val prop = Properties()
            FileInputStream("ena-url.properties").use {
                prop.load(it)
            }
            fun exception(name: String) = InvalidParameterException("'$name' is not set in ena-url.properties")
            testServer = prop.getProperty("test-server") ?: throw exception("test-server")
            productionServer = prop.getProperty("production-server") ?: throw exception("production-server")
            ftpServer = prop.getProperty("ftp-server") ?: throw exception("ftp-server")
        }
    }
}

enum class EnaServer(val url: String) {
    TEST("${EnaUrls.testServer}?auth=ENA%20${EnaCredentials.user}%20${EnaCredentials.password}"),
    PRODUCTION("${EnaUrls.productionServer}?auth=ENA%20${EnaCredentials.user}%20${EnaCredentials.password}")
}

data class SubmissionResult(var success: Boolean = false,
                            var submissionAcc: String = "",
                            var analysisAcc: String = "",
                            var error: String = "")

fun submitToEna(submissionXml: String, analysisXml: String, enaServer: EnaServer): SubmissionResult {
    val (submissionFile, analysisFile) = createXmlFiles(analysisXml, submissionXml)

    val post = HttpPost(enaServer.url)
    post.entity = MultipartEntityBuilder.create()
            .addPart("SUBMISSION", FileBody(submissionFile, ContentType.TEXT_XML, "submission.xml"))
            .addPart("ANALYSIS", FileBody(analysisFile, ContentType.TEXT_XML, "analysis.xml"))
            .build()
    val httpClient = createCertificateIgnoringHttpclient()
    val response = httpClient.execute(post)
    val responseString = IOUtils.toString(response.entity.content)
    return parseResult(responseString)
}

private fun createXmlFiles(analysisXml: String, submissionXml: String): Pair<File, File> {
    val submissionFile = File.createTempFile("ena_submission", ".xml")
    submissionFile.deleteOnExit()
    FileWriter(submissionFile).use {
        IOUtils.copy(StringReader(submissionXml), it)
    }

    val analysisFile = File.createTempFile("ena_analysis", ".xml")
    analysisFile.deleteOnExit()
    FileWriter(analysisFile).use {
        IOUtils.copy(StringReader(analysisXml), it)
    }
    return Pair(submissionFile, analysisFile)
}

private fun createCertificateIgnoringHttpclient(): CloseableHttpClient {
    val sslContext = SSLContexts.custom().loadTrustMaterial(null, { certificateChain, autType -> true }).build()
    val socketFactory = SSLConnectionSocketFactory(sslContext)
    val httpClient = HttpClients.custom().setSSLSocketFactory(socketFactory).build()
    return httpClient
}

private fun parseResult(responseString: String): SubmissionResult {
    val result = SubmissionResult()
    val xmlInputFactory = XMLInputFactory.newFactory()
    val reader = xmlInputFactory.createXMLStreamReader(StringReader(responseString))
    while (reader.hasNext()) {
        reader.next()
        if (reader.isStartElement) {
            val tagName = reader.localName
            when (tagName) {
                "RECEIPT" -> reader.getAttributeValue(null, "success")?.let { success ->
                    result.success = success.toBoolean()
                }
                "SUBMISSION" -> reader.getAttributeValue(null, "accession")?.let { submissionAcc ->
                    result.submissionAcc = submissionAcc
                }
                "ANALYSIS" -> reader.getAttributeValue(null, "accession")?.let { analysisAcc ->
                    result.analysisAcc = analysisAcc
                }
                "ERROR" -> {
                    reader.next()
                    result.error = reader.text
                }
            }
        }
    }
    return result
}

private fun enaFtp(operations: FTPClient.() -> Unit) {
    with(FTPClient()) {
        connect(EnaUrls.ftpServer)
        login(EnaCredentials.user, EnaCredentials.password)
        operations()
        logout()
        if (isConnected) disconnect()
    }
}

fun uploadToEnaFtp(file: File) {
    enaFtp {
        setFileType(FTP.BINARY_FILE_TYPE)
        bufferSize = 10 * 1024 * 1024

        FileInputStream(file).use {
            storeFile(file.name, it)
        }
    }
}

internal fun deleteFromEnaFtp(file: File) {
    enaFtp {
        deleteFile(file.name)
    }
}