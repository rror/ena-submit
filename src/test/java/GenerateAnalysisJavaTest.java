import kotlin.Pair;
import kotlin.Unit;
import noNamespace.ANALYSISDocument;
import org.apache.xmlbeans.XmlException;
import org.junit.Test;

import java.util.GregorianCalendar;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class GenerateAnalysisJavaTest {

	@Test(expected = EnaXmlException.class)
	public void createIncompleteAnalysisTest() {
		GenerateAnalysisKt.analysis(analysis -> {
			analysis.setTitle("Capturing Extant Variation from a Genome in Flux: Maize HapMap II");
			return Unit.INSTANCE;
		});
	}

	@Test
	public void createVcfAnalysisTest() throws XmlException {
		Pair<String, String> xmls = GenerateAnalysisKt.analysis(analysis -> {
			analysis.setAlias("Maize HapMap test");
			analysis.setCenterName("CSHL");
			analysis.setAnalysisCenter("CSHL");
			analysis.setBrokerName("ENSEMBL GENOMES");
			analysis.setHoldDate(new GregorianCalendar(2020, 11, 24).getTime()); // month is 0 based
			analysis.setTitle("Capturing Extant Variation from a Genome in Flux: Maize HapMap II");
			analysis.setDescription("A comprehensive characterization of genetic variation across 103 inbred lines ...");
			analysis.sampleMapping(mapping -> {
				mapping.add("IRGC103469/IRGC103469_aln_sorted.bam", "SRS302388");
				mapping.add("TOG7102/TOG7102_aln_sorted.bam", "SRS302394");
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

		String analysisXml = xmls.getSecond();
		checkAnalysisContent(analysisXml);
		assertThat(analysisXml, containsString("<SEQUENCE_VARIATION>"));
		assertThat(analysisXml, containsString(" filetype=\"vcf\" "));
	}

	private static void checkAnalysisContent(String analysisXml) throws XmlException {
		ANALYSISDocument analysis = ANALYSISDocument.Factory.parse(analysisXml);
		assertThat(analysis.validate(), is(true));
		assertThat(analysisXml, containsString("<STUDY_REF accession=\"SRP011907\"/>"));
		assertThat(analysisXml, containsString("<STANDARD accession=\"GCA_000005005.2\"/>"));
		assertThat(analysisXml, containsString("<SEQUENCE accession=\"GK000031.2\" label=\"1\"/>"));
		assertThat(analysisXml, containsString("<SAMPLE_REF label=\"IRGC103469/IRGC103469_aln_sorted.bam\" accession=\"SRS302388\"/>"));
		assertThat(analysisXml, containsString("<SAMPLE_REF label=\"TOG7102/TOG7102_aln_sorted.bam\" accession=\"SRS302394\"/>"));
	}
}
