# ENA cram processing tools
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)

Typical cramtools usage in processing:

- cram dump
`java  -XX:+UseSerialGC -Xmx10G -Dsamjdk.use_cram_ref_download=true -Djava.io.tmpdir="/fire/staging/era/tmp" -jar cramtools.jar fastq --reverse --gzip $@`
- cram stats
`java -XX:+UseSerialGC -Xmx4G -cp cramtools.jar net.sf.cram.CramTools bam -c -F 2304 -I $@`
