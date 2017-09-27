#cramtools usage in ENA processing:

   [era@oy-ena-login-2 ~]$ cat /homes/era/tools/putils/cram_dump
   #! /bin/bash
   java  -XX:+UseSerialGC -Xmx10G
   -Dsamjdk.use_cram_ref_download=true
   -Djava.io.tmpdir="/fire/staging/era/tmp" -jar
   /homes/era/lib/cramtools.jar fastq --reverse --gzip $@

   [era@oy-ena-login-2 ~]$ cat /homes/era/tools/putils/cram_stats
   #java -Xmx4G -jar /homes/era/lib/cramtools.jar bam -c -I $@
   java -XX:+UseSerialGC -Xmx4G -cp
   /homes/era/lib/commons-compress-1.9.jar:/homes/era/lib/cramtools.jar
   net.sf.cram.CramTools bam -c -F 2304 -I $@ 
