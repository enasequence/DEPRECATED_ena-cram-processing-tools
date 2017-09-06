package htsjdk.samtools.cram.encoding.reader;

public interface IRead extends Comparable<IRead> {
	public long getAge();

	public void setAge(long age);
}