import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.startsWith
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.*
import org.hamcrest.CoreMatchers.`is` as is_


class SubmitTest {
    val vcfFile = File(javaClass.classLoader.getResource("Glab_var_chr1_flt.vcf").file)

    @Before
    fun uploadVcf() {
        uploadToEnaFtp(vcfFile)
    }

    @After
    fun deleteVcf() {
        deleteFromEnaFtp(vcfFile)
    }

    @Test
    fun submitVcfToEna() {
        val (submissionXml, analysisXml) = analysis {
            alias("Maize HapMap test")
            centerName("CSHL")
            brokerName("ENSEMBL GENOMES")
            title("Capturing Extant Variation from a Genome in Flux: Maize HapMap II")
            description("A comprehensive characterization of genetic variation across 103 inbred lines ...")
            sampleMapping {
                +"IRGC103469/IRGC103469_aln_sorted.bam".to("SRS302388")
                +"TOG5457/TOG5457_aln_sorted.bam".to("SRS302389")
                +"TOG5467/TOG5467_aln_sorted.bam".to("SRS302390")
            }
            studyReference("SRP011907")
            runReference("SRR447750")
            vcf {
                fileName("Glab_var_chr1_flt_1k.vcf")
                md5("10899e2ca49b37c8c37c4763616496ac")
                assemblyAccession("GCA_000005005.2")
                sequenceMapping {
                    +"1".to("GK000031.2")
                }
            }
        }

        val result = submitToEna(submissionXml, analysisXml, EnaServer.TEST)
        if (result.error == "Server error. Please contact us if the problem persists.") {
            println("ENA submission server is down. Unable to test submission.")
        }
        // Submissions to the ENA test server are deleted every 24 hours,
        // so only the first test submission of the day will go though.
        // Alternative: create unique alias and file name for every test invocation.
        else if (!result.error.contains("Submission with name Maize HapMap test already exists")) {
            assertThat(result.success, is_(true))
            assertThat(result.submissionAcc, startsWith("ERA"))
            assertThat(result.analysisAcc, startsWith("ERZ"))
            assertThat(result.error, is_(""))
        }
    }

    @Test
    fun submitInvalidAccessionToEna() {
        val aliasSuffix = Random().ints(10, 0, 10).toArray().joinToString("")
        val (submissionXml, analysisXml) = analysis {
            alias("alias" + aliasSuffix)
            centerName("CSHL")
            title("title")
            description("description")
            studyReference("SRP011907")
            runReference("this is not a valid accession")
            vcf {
                fileName("Glab_var_chr1_flt_1k.vcf")
                md5("10899e2ca49b37c8c37c4763616496ac")
                sequenceMapping {
                    +"1".to("GK000031.2")
                }
            }
        }

        val result = submitToEna(submissionXml, analysisXml, EnaServer.TEST)
        if (result.error == "Server error. Please contact us if the problem persists.") {
            println("ENA submission server is down. Unable to test submission.")
        } else {
            assertThat(result.success, is_(false))
            assertThat(result.error, containsString("found unknown run with (accession=this is not a valid accession)"))
        }
    }
}