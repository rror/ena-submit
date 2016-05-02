import kotlin.Pair;
import kotlin.Unit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class SubmitJavaTest {
	private static File vcfFile = new File(
			SubmitJavaTest.class.getClassLoader().getResource("Glab_var_chr1_flt.vcf").getFile());

	@Before
	public void uploadVcf() {
		SubmitKt.uploadToEnaFtp(vcfFile);
	}

	@After
	public void deleteVcf() {
		SubmitKt.deleteFromEnaFtp(vcfFile);
	}

	@Test
	public void submitVcfToEna() {
		Pair<String, String> xmls = GenerateAnalysisKt.analysis(analysis -> {
			analysis.setAlias("Maize HapMap test");
			analysis.setCenterName("CSHL");
			analysis.setBrokerName("ENSEMBL GENOMES");
			analysis.setTitle("Capturing Extant Variation from a Genome in Flux: Maize HapMap II");
			analysis.setDescription("A comprehensive characterization of genetic variation across 103 inbred lines ...");
			analysis.sampleMapping(mapping -> {
				mapping.add("IRGC103469/IRGC103469_aln_sorted.bam", "SRS302388");
				mapping.add("TOG5457/TOG5457_aln_sorted.bam", "SRS302389");
				mapping.add("TOG5467/TOG5467_aln_sorted.bam", "SRS302390");
				return Unit.INSTANCE;
			});
			analysis.setStudyReference("SRP011907");
			analysis.setRunReference("SRR447750");
			analysis.vcf(analysisFile -> {
				analysisFile.setFileName("Glab_var_chr1_flt_1k.vcf");
				analysisFile.setMd5("10899e2ca49b37c8c37c4763616496ac");
				analysisFile.setAssemblyReference("GCA_000005005.2");
				analysisFile.sequenceMapping(mapping -> {
					mapping.add("1", "GK000031.2");
					return Unit.INSTANCE;
				});
				return Unit.INSTANCE;
			});
			return Unit.INSTANCE;
		});
		String submissionXml = xmls.getFirst();
		String analysisXml = xmls.getSecond();

		SubmissionResult result = SubmitKt.submitToEna(submissionXml, analysisXml, EnaServer.TEST);
		if (result.getError().equals("Server error. Please contact us if the problem persists.")
				|| result.getError().contains("don't exist in the drop box or upload area /fire/staging/era/upload")) {
			System.out.println("ENA submission server is down. Unable to test submission.");
		} else if (!result.getError().contains("Submission with name Maize HapMap test already exists")) {
			assertThat(result.getSuccess(), is(true));
			assertThat(result.getSubmissionAcc(), startsWith("ERA"));
			assertThat(result.getAnalysisAcc(), startsWith("ERZ"));
			assertThat(result.getError(), is(""));
		}
	}
}
