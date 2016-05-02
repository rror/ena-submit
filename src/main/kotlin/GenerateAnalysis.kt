import noNamespace.*
import org.apache.commons.codec.binary.Hex
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.NullOutputStream
import org.apache.xmlbeans.XmlObject
import org.apache.xmlbeans.XmlOptions
import java.io.File
import java.io.FileInputStream
import java.io.StringWriter
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*

class EnaXmlException(message: String) : Exception(message)

/**
 * Entry method to construct 'analysis' and 'submission' XML documents
 */
fun analysis(init: Analysis.() -> Unit): Pair<String, String> {
    val analysis = Analysis()
    analysis.init()
    return analysis.createDocuments()
}

/**
 * Holds all metadata necessary to create the 'analysis' XML document
 */
class Analysis() {
    private val analysisDoc = ANALYSISDocument.Factory.newInstance()
    private val analysis = analysisDoc.addNewANALYSIS()

    private val submission = Submission()

    var alias = ""
        set(value) {
            analysis.alias = value
            submission.alias = value
        }

    var centerName = ""
        set(value) {
            analysis.centerName = value
            submission.centerName = value
        }

    var analysisCenter = ""
        set(value) {
            analysis.analysisCenter = value
        }

    var brokerName = ""
        set(value) {
            analysis.brokerName = value
            submission.brokerName = value
        }

    var title = ""
        set(value) {
            analysis.title = value
        }

    var description = ""
        set(value) {
            analysis.description = value
        }

    var holdDate = Date()
        set(value) {
            submission.holdDate = value
        }

    fun sampleMapping(init: Mapping.() -> Unit) {
        val mapping = Mapping()
        mapping.init()
        for ((sample, accession) in mapping.pairs) {
            val sampleRef = analysis.addNewSAMPLEREF()
            sampleRef.label = sample
            sampleRef.accession = accession
        }
    }

    var studyReference = ""
        set(value) {
            analysis.addNewSTUDYREF().accession = value
        }

    var runReference = ""
        set(value) {
            analysis.addNewRUNREF().accession = value
        }

    fun vcf(init: AnalysisFile.() -> Unit) {
        val vcf = AnalysisFile(analysis, FileType.VCF)
        vcf.init()
    }

    fun bam(init: AnalysisFile.() -> Unit) {
        val bam = AnalysisFile(analysis, FileType.BAM)
        bam.init()
    }

    fun cram(init: AnalysisFile.() -> Unit) {
        val cram = AnalysisFile(analysis, FileType.CRAM)
        cram.init()
    }

    private fun create(): String {
        throwXmlErrors(analysisDoc)
        val writer = StringWriter()
        analysisDoc.save(writer, XmlOptions().setSavePrettyPrint())
        return writer.toString()
    }

    internal fun createDocuments(): Pair<String, String> {
        return Pair(submission.create(), this.create())
    }
}

class Mapping() {
    val pairs = arrayListOf<Pair<String, String>>()
    operator fun Pair<String, String>.unaryPlus() {
        pairs.add(this)
    }

    /**
     * Convenience function for Java
     */
    fun add(first: String, second: String) = +first.to(second)
}

private fun throwXmlErrors(xml: XmlObject) {
    val errors = arrayListOf<String>()
    val isValid = xml.validate(XmlOptions().setErrorListener(errors))
    if (!isValid) {
        throw EnaXmlException(errors.joinToString("\n"))
    }
}

/**
 * Holds all metadata necessary to create the 'submission' XML document
 */
private class Submission() {
    private val submissionDoc = SUBMISSIONDocument.Factory.newInstance()
    private val submission = submissionDoc.addNewSUBMISSION()
    private val actions = submission.addNewACTIONS()

    init {
        val addAnalysis = actions.addNewACTION().addNewADD()
        addAnalysis.source = "analysis.xml"
        addAnalysis.schema = SubmissionType.ACTIONS.ACTION.ADD.Schema.ANALYSIS
    }

    var alias = ""
        set(value) {
            submission.alias = value
        }

    var centerName = ""
        set(value) {
            submission.centerName = value
        }

    var brokerName = ""
        set(value) {
            submission.brokerName = value
        }

    var holdDate = Date()
        set(value) {
            if (value.after(Date())) {
                val calendar = Calendar.getInstance().apply { this.time = value }
                val holdAction = actions.addNewACTION().addNewHOLD()
                holdAction.holdUntilDate = calendar
            }
        }

    internal fun create(): String {
        throwXmlErrors(submissionDoc)
        val writer = StringWriter()
        submissionDoc.save(writer, XmlOptions().setSavePrettyPrint())
        return writer.toString()
    }
}

enum class FileType {
    VCF, BAM, CRAM
}

/**
 * Setters for the 'file' part inside the 'analysis' XML document
 */
class AnalysisFile(val analysis: AnalysisType, val fileType: FileType) {
    private val analysisFile = analysis.addNewFILES().addNewFILE()
    private val assembly = Assembly(analysis, fileType)

    var fileName = ""
        set(value) {
            analysisFile.filename = value
            analysisFile.filetype = when (fileType) {
                FileType.VCF -> AnalysisFileType.Filetype.VCF
                FileType.BAM -> AnalysisFileType.Filetype.BAM
                FileType.CRAM -> AnalysisFileType.Filetype.CRAM
            }
        }

    var md5 = ""
        set(value) {
            analysisFile.checksumMethod = AnalysisFileType.ChecksumMethod.MD_5
            analysisFile.checksum = value
        }

    var file = File("")
        set(value) {
            fileName = value.name
            val digest = MessageDigest.getInstance("MD5")
            FileInputStream(value).use {
                IOUtils.copy(DigestInputStream(it, digest), NullOutputStream())
            }
            val checksum = Hex.encodeHexString(digest.digest())
            md5 = checksum
        }

    var assemblyReference = ""
        set(value) {
            assembly.accession = value
        }

    fun sequenceMapping(init: Mapping.() -> Unit) = assembly.sequenceMapping(init)
}

/**
 * Setters for the 'assembly' part inside the 'analysis' XML document
 */
private class Assembly(analysis: AnalysisType, fileType: FileType) {
    private val type = analysis.addNewANALYSISTYPE()

    private val refType = when (fileType) {
        FileType.VCF -> type.addNewSEQUENCEVARIATION().apply {
            val experimentType = this.addNewEXPERIMENTTYPE()
            experimentType.set(AnalysisType.ANALYSISTYPE.SEQUENCEVARIATION.EXPERIMENTTYPE.WHOLE_GENOME_SEQUENCING)
        }
        else -> type.addNewREFERENCEALIGNMENT()
    }

    internal var accession = ""
        set(value) {
            val assembly = refType.addNewASSEMBLY().addNewSTANDARD()
            assembly.accession = value
        }

    internal fun sequenceMapping(init: Mapping.() -> Unit) {
        val mapping = Mapping()
        mapping.init()
        for ((chromosome, accession) in mapping.pairs) {
            val sequence = refType.addNewSEQUENCE()
            sequence.accession = accession
            sequence.label = chromosome
        }
    }
}


