import noNamespace.ANALYSISDocument
import noNamespace.SUBMISSIONDocument
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.reflect.KFunction2
import org.hamcrest.CoreMatchers.`is` as is_

class GenerateAnalysisTest {
    @Test
    fun createSubmissionTest() {
        val (submissionXml, analysisXml) = createXmls(Analysis::vcf)
        val submission = SUBMISSIONDocument.Factory.parse(submissionXml)
        assertThat(submission.validate(), is_(true))
        assertThat(submissionXml, containsString("HOLD HoldUntilDate=\"2020-12-24Z\"/>"));
    }

    @Test
    fun createSubmissionHoldTest() {
        // set hold date to current day, should be ignored
        val (submissionXml, analysisXml) = analysis {
            title("")
            description("")
            studyReference("")
            holdDate(Date())
            vcf {
                fileName("")
                md5("")
            }
        }
        assertThat(submissionXml, not(containsString("HOLD")));
    }

    @Test(expected = EnaXmlException::class)
    fun createIncompleteAnalysisTest() {
        analysis {
            title("Capturing Extant Variation from a Genome in Flux: Maize HapMap II")
        }
    }

    @Test
    fun createVcfAnalysisTest() {
        val (submissionXml, analysisXml) = createXmls(Analysis::vcf)
        checkAnalysisContent(analysisXml)
        assertThat(analysisXml, containsString("<SEQUENCE_VARIATION>"))
        assertThat(analysisXml, containsString(""" filetype="vcf" """))
    }

    @Test
    fun createBamAnalysisTest() {
        val (submissionXml, analysisXml) = createXmls(Analysis::bam)
        checkAnalysisContent(analysisXml)
        assertThat(analysisXml, containsString("<REFERENCE_ALIGNMENT>"));
        assertThat(analysisXml, containsString(""" filetype="bam" """))
    }

    @Test
    fun createCramAnalysisTest() {
        val (submissionXml, analysisXml) = createXmls(Analysis::cram)
        checkAnalysisContent(analysisXml)
        assertThat(analysisXml, containsString("<REFERENCE_ALIGNMENT>"));
        assertThat(analysisXml, containsString(""" filetype="cram" """))
    }

    private fun createXmls(analysisFile: KFunction2<Analysis, AnalysisFile.() -> Unit, Unit>): Pair<String, String> {
        return analysis {
            alias("Maize HapMap test")
            centerName("CSHL")
            analysisCenter("CSHL")
            brokerName("ENSEMBL GENOMES")
            holdDate(GregorianCalendar(2020, 11, 24).time) // month is 0 based
            title("Capturing Extant Variation from a Genome in Flux: Maize HapMap II")
            description("A comprehensive characterization of genetic variation across 103 inbred lines ...")
            sampleMapping {
                +"IRGC103469/IRGC103469_aln_sorted.bam".to("SRS302388")
                +"TOG7102/TOG7102_aln_sorted.bam".to("SRS302394")
                +"TOG5467/TOG5467_aln_sorted.bam".to("SRS302390")
            }
            studyReference("SRP011907")
            runReference("SRR447750")
            analysisFile(this) {
                fileName("Glab_var_chr1_flt_1k.vcf")
                md5("10899e2ca49b37c8c37c4763616496ac")
                assemblyAccession("GCA_000005005.2")
                sequenceMapping {
                    +"1".to("GK000031.2")
                }
            }
        }
    }

    private fun checkAnalysisContent(analysisXml: String) {
        val analysis = ANALYSISDocument.Factory.parse(analysisXml)
        assertThat(analysis.validate(), is_(true))
        analysisXml.let {
            assertThat(it, containsString("""<STUDY_REF accession="SRP011907"/>"""));
            assertThat(it, containsString("""<STANDARD accession="GCA_000005005.2"/>"""));
            assertThat(it, containsString("""<SEQUENCE accession="GK000031.2" label="1"/>"""));
            assertThat(it, containsString("""<SAMPLE_REF label="IRGC103469/IRGC103469_aln_sorted.bam" accession="SRS302388"/>"""));
            assertThat(it, containsString("""<SAMPLE_REF label="TOG7102/TOG7102_aln_sorted.bam" accession="SRS302394"/>"""));
        }
    }

    @Test
    fun createFileAnalysisTest() {
        val vcfFile = File(javaClass.classLoader.getResource("Glab_var_chr1_flt.vcf").file)
        val (submissionXml, analysisXml) = analysis {
            title("")
            description("")
            studyReference("")
            holdDate(Date())
            vcf {
                fileName(vcfFile)
                md5(vcfFile)
            }
        }
        assertThat(analysisXml, containsString(""" filename="Glab_var_chr1_flt.vcf" """))
        assertThat(analysisXml, containsString(""" checksum="b2e4fb01320ae6f52e4bb87a2fc199d0""""))
    }
}