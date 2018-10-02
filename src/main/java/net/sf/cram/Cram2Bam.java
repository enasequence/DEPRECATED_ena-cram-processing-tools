/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package net.sf.cram;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.FileConverter;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.build.ContainerParser;
import htsjdk.samtools.cram.build.Cram2SamRecordFactory;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.build.CramNormalizer;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.Log;
import net.sf.cram.CramTools.LevelConverter;
import net.sf.cram.ref.ENAReferenceSource;

public class Cram2Bam {
	private static Log log = Log.getInstance(Cram2Bam.class);
	public static final String COMMAND = "bam";

	private static void printUsage(JCommander jc) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n");
		jc.usage(sb);

		System.out.println("Version " + Cram2Bam.class.getPackage().getImplementationVersion());
		System.out.println(sb.toString());
	}

	public static void main(String[] args) throws IOException, IllegalArgumentException, IllegalAccessException {
		Params params = new Params();
		JCommander jc = new JCommander(params);
		try {
			jc.parse(args);
		} catch (Exception e) {
			System.out.println("Failed to parse parameteres, detailed message below: ");
			System.out.println(e.getMessage());
			System.out.println();
			System.out.println("See usage: -h");
			System.exit(1);
		}

		if (args.length == 0 || params.help) {
			printUsage(jc);
			System.exit(1);
		}

		Log.setGlobalLogLevel(params.logLevel);

		if (params.reference == null)
			log.warn("No reference file specified, remote access over internet may be used to download public sequences. ");

		InputStream is = null;
		try {
			is = new SeekableFileStream(params.cramFile);			
		} catch (Exception e2) {
			log.error("Failed to open CRAM from: " + params.cramFile, e2);
			System.exit(1);
		}

		CramHeader cramHeader = CramIO.readCramHeader(is);

		CRAMReferenceSource referenceSource = new ENAReferenceSource( /* params.reference */ );
		( (ENAReferenceSource)referenceSource).setDownloadTriesBeforeFailing(params.downloadTriesBeforeFailing);
		
		

		BlockCompressedOutputStream.setDefaultCompressionLevel(Defaults.COMPRESSION_LEVEL);
		SAMFileWriterFactory samFileWriterFactory = new SAMFileWriterFactory();
		samFileWriterFactory.setAsyncOutputBufferSize(params.asyncBamBuffer);
		samFileWriterFactory.setCreateIndex(false);
		samFileWriterFactory.setCreateMd5File(false);
		samFileWriterFactory.setUseAsyncIo(params.syncBamOutput);		

		htsjdk.samtools.cram.structure.Container c = null;		

		long recordCount = 0;
		long baseCount = 0;
		long readTime = 0;
		long parseTime = 0;
		long normTime = 0;
		long samTime = 0;
		long writeTime = 0;
		long time = 0;
		ArrayList<CramCompressionRecord> cramRecords = new ArrayList<CramCompressionRecord>(10000);		

		ContainerParser parser = new ContainerParser(cramHeader.getSamFileHeader());
		while (true) {

			time = System.nanoTime();
			c = ContainerIO.readContainer(cramHeader.getVersion(), is);
			if (c.isEOF())
				break;

			readTime += System.nanoTime() - time;

			if (params.countOnly && params.requiredFlags == 0 && params.filteringFlags == 0) {
				recordCount += c.nofRecords;
				baseCount += c.bases;
				continue;
			}

			time = System.nanoTime();
			cramRecords.clear();
			parser.getRecords(c, cramRecords, ValidationStringency.SILENT);
			parseTime += System.nanoTime() - time;

			Cram2SamRecordFactory c2sFactory = new Cram2SamRecordFactory(cramHeader.getSamFileHeader());

			long c2sTime = 0;
			long sWriteTime = 0;

			boolean enough = false;
			for (CramCompressionRecord r : cramRecords) {
				// enforcing a special way to calculate template size:
				restoreMateInfo(r);
				
				time = System.nanoTime();
				SAMRecord s = c2sFactory.create(r);

				if (params.requiredFlags != 0 && ((params.requiredFlags & s.getFlags()) == 0))
					continue;
				if (params.filteringFlags != 0 && ((params.filteringFlags & s.getFlags()) != 0))
					continue;
				if (params.countOnly) {
					recordCount++;
					baseCount += r.readLength;
					continue;
				}
			}

			log.info(String
					.format("CONTAINER READ: io %dms, parse %dms,convert %dms, BAM write %dms, %d bases in %d records",
							c.readTime / 1000000, c.parseTime / 1000000, c2sTime / 1000000,
							sWriteTime / 1000000, c.bases, c.nofRecords));

			if (enough || (params.outputFile == null && System.out.checkError()))
				break;
		}

		if (params.countOnly) {
			System.out.printf("READS: %d; BASES: %d\n", recordCount, baseCount);
		}

		

		log.warn(String.format("TIMES: io %ds, parse %ds, norm %ds, convert %ds, BAM write %ds", readTime / 1000000000,
				parseTime / 1000000000, normTime / 1000000000, samTime / 1000000000, writeTime / 1000000000));
	}

	private static void restoreMateInfo(CramCompressionRecord r) {
		if (r.next == null) {
			return;
		}
		CramCompressionRecord cur;
		cur = r;
		while (cur.next != null) {
			setNextMate(cur, cur.next);
			cur = cur.next;
		}

		// cur points to the last segment now:
		CramCompressionRecord last = cur;
		setNextMate(last, r);

		final int templateLength = CramNormalizer.computeInsertSize(r, last);
		r.templateSize = templateLength;
		last.templateSize = -templateLength;
	}

	private static void setNextMate(CramCompressionRecord r, CramCompressionRecord next) {
		r.mateAlignmentStart = next.alignmentStart;
		r.setMateUnmapped(next.isSegmentUnmapped());
		r.setMateNegativeStrand(next.isNegativeStrand());
		r.mateSequenceID = next.sequenceId;
		if (r.mateSequenceID == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)
			r.mateAlignmentStart = SAMRecord.NO_ALIGNMENT_START;
	}


	@Parameters(commandDescription = "CRAM to BAM conversion. ")
	static class Params {
		@Parameter(names = { "-l", "--log-level" }, description = "Change log level: DEBUG, INFO, WARNING, ERROR.", converter = LevelConverter.class)
		Log.LogLevel logLevel = Log.LogLevel.ERROR;

		@Parameter(names = { "--input-cram-file", "-I" }, description = "The path to the CRAM file to uncompress. Omit if standard input (pipe).")
		File cramFile;

//		@Parameter(names = { "--reference-fasta-file", "-R" }, converter = FileConverter.class, description = "Path to the reference fasta file, it must be uncompressed and indexed (use 'samtools faidx' for example). ")
		File reference;

		@Parameter(names = { "--output-bam-file", "-O" }, converter = FileConverter.class, description = "The path to the output BAM file.")
		File outputFile;

		@Parameter(names = { "-b", "--output-bam-format" }, description = "Output in BAM format.")
		boolean outputBAM = false;

		@Parameter(names = { "-q", "--output-fastq-format" }, hidden = true, description = "Output in fastq format.")
		boolean outputFastq = false;

		@Parameter(names = { "-z", "--output-fastq-gz-format" }, hidden = true, description = "Output in gzipped fastq format.")
		boolean outputFastqGz = false;

		@Parameter(names = { "--print-sam-header" }, description = "Print SAM header when writing SAM format.")
		boolean printSAMHeader = false;

		@Parameter(names = { "-h", "--help" }, description = "Print help and quit")
		boolean help = false;

		@Parameter(names = { "--default-quality-score" }, description = "Use this quality score (decimal representation of ASCII symbol) as a default value when the original quality score was lost due to compression. Minimum is 33.")
		int defaultQS = '?';

		@Parameter(names = { "--calculate-md-tag" }, description = "Calculate MD tag.")
		boolean calculateMdTag = false;

		@Parameter(names = { "--calculate-nm-tag" }, description = "Calculate NM tag.")
		boolean calculateNmTag = false;

		@Parameter(names = { "--decrypt" }, description = "Decrypt the file.")
		boolean decrypt = false;

		@Parameter(names = { "--count-only", "-c" }, description = "Count number of records.")
		boolean countOnly = false;

		@Parameter(names = { "--required-flags", "-f" }, description = "Required flags. ")
		int requiredFlags = 0;

		@Parameter(names = { "--filter-flags", "-F" }, description = "Filtering flags. ")
		int filteringFlags = 0;

		@Parameter(names = { "--inject-sq-uri" }, description = "Inject or change the @SQ:UR header fields to point to ENA reference service. ")
		public boolean injectURI = false;

		@Parameter(names = { "--sync-bam-output" }, description = "Write BAM output in the same thread.")
		public boolean syncBamOutput = false;

		@Parameter(names = { "--async-bam-buffer" }, description = "The buffer size (number of records) for the asynchronious BAM output.", hidden = true)
		int asyncBamBuffer = 10000;

		@Parameter(names = { "--ignore-md5-mismatch" }, description = "Issue a warning on sequence MD5 mismatch and continue. This does not garantee the data will be read succesfully. ")
		public boolean ignoreMD5Mismatch = false;

		@Parameter(names = { "--skip-md5-check" }, description = "Skip MD5 checks when reading the header.")
		public boolean skipMD5Checks = false;

		@Parameter(names = { "--ref-seq-download-tries" }, description = "Try to download sequences this many times if their md5 mismatches.", hidden = true)
		int downloadTriesBeforeFailing = 2;

		@Parameter(names = { "--resilient" }, description = "Report reference sequence md5 mismatch and keep going.", hidden = true)
		public boolean resilient = false;

		@Parameter(names = { "--password", "-p" }, description = "Password to decrypt the file.")
		public String password;

		@Parameter(names = { "--load-whole-reference-sequence" }, description = "Load all bases for each reference sequence required. ", hidden = true)
		public boolean loadWholeReferenceSequence = false;
	}

}
