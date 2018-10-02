package net.sf.cram;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

import htsjdk.samtools.util.Log;


public class 
Cram2FastqTest 
{

	@Test public void
	convertCram2Fastq() throws FileNotFoundException, IOException, Exception
	{
		File output = File.createTempFile( "FASTQ", "FASTQ" );
		output.delete();
		
		Cram2Fastq.Params params = new Cram2Fastq.Params();
		URL url = Cram2FastqTest.class.getClassLoader().getResource( "net/sf/cram/SRR2989699.cram" );
		File file = new File( url.getFile() );

		params.cramFile = file;
		params.reverse = true;
		params.nofStreams = 3;
		params.fastqBaseName = output.getPath();
		Log.setGlobalLogLevel( params.logLevel );
		Cram2Fastq.main2( params );
		
		Assert.assertEquals( "@SRR2989699.5\n"
						   + "CGCCACGAGCTGGTTGTCTATGGGACAAGTGATGTGGTTGATAACCTCCCATTGCTATCTCA\n"
						   + "+\n"
						   + "EEEEEBEEEEEEEADDEEDEFFBABDEDEBBDADAFEBFAFAFFBFEFFEFDFEF=FAFFFD\n"
						   + "@SRR2989699.4\n"
						   + "CGAAGAACAGATGGCTCGTACCAAGCAAACCGCTCGTAAGTCCACCGGAGGTAAAGCTCCAA\n"
						   + "+\n"
						   + "FGGFGFFGBGFEFGGFGA=GFEGGGFGGGFGGFEGEDGDFFFFFFDBDDFE?EBE?B@C@AE\n"
						   + "@SRR2989699.1\n"
						   + "TCGTTCTTTGCCTCCGGGATTGTGAGCCAACATAATGCAATCGACATGGACTACTTGTTTGG\n"
						   + "+\n"
						   + "5DEGEGAGDFGGGGFDGG?GFGFDGGGGAGGEGEGGEGGGGFGDFDFGF=F=F=FFF5FADE\n"
						   + "@SRR2989699.6\n"
						   + "CAACAACCTTCTCAGCAACTAAGAAAGCAGAGTAAAACCCAACACCAAACTGTCCGACAATG\n"
						   + "+\n"
						   + "GGGGEGFGDFFFFEFFFGEEFFEFFFGEGGGGGGFCFEFFFDGGEDFEFDEDEFEEAD=ECD\n",
				             new String( Files.readAllBytes( Paths.get( output.getPath() + ".fastq" ) ), StandardCharsets.UTF_8 ) );
	}
}
