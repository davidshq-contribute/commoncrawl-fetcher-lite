/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tallison.cc.index.fetcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tika.config.Initializable;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.emitter.StreamEmitter;
import org.apache.tika.pipes.emitter.fs.FileSystemEmitter;
import org.apache.tika.pipes.emitter.s3.S3Emitter;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.RangeFetcher;
import org.apache.tika.pipes.fetcher.s3.S3Fetcher;
import org.apache.tika.utils.StringUtils;
import org.tallison.cc.index.IndexIterator;
import org.tallison.cc.index.io.BackoffHttpFetcher;
import org.tallison.cc.index.io.TargetPathRewriter;
import org.tallison.cc.index.selector.RecordSelector;

public class FetcherConfig {

    public static String CC_HTTPS_BASE = "https://data.commoncrawl.org";

    public static String CC_S3_BUCKET = "commoncrawl";

    public static String CC_REGION = "us-east-1";

    public static String DEFAULT_FS_DOCS_PATH = "docs";

    public static long[] DEFAULT_THROTTLE_SECONDS = new long[]{30, 120, 600, 1800};
    private int numThreads = 2;
    //maximum records to read
    private long maxRecords = -1;

    //maximum files extracted from cc
    private long maxFilesExtracted = -1;
    //maximum files written to 'truncated' list.
    private long maxFilesTruncated = -1;

    private Path indexPathsFile;

    private Path truncatedUrlsFile = Paths.get("urls-for-truncated-files.txt");

    private String targetPathPattern = "";

    private boolean dryRun = false;

    private RecordSelector recordSelector = RecordSelector.ACCEPT_ALL_RECORDS;

    @JsonProperty("indices")
    private IndexIterator indexIterator;

    @JsonProperty("fetcher")
    private FetchConfig fetchConfig;

    @JsonProperty("docs")
    private EmitConfig emitConfig;

    public static String getCcHttpsBase() {
        return CC_HTTPS_BASE;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    public long getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(long maxRecords) {
        this.maxRecords = maxRecords;
    }

    public long getMaxFilesExtracted() {
        return maxFilesExtracted;
    }

    public void setMaxFilesExtracted(long maxFilesExtracted) {
        this.maxFilesExtracted = maxFilesExtracted;
    }

    public long getMaxFilesTruncated() {
        return maxFilesTruncated;
    }

    public void setMaxFilesTruncated(long maxFilesTruncated) {
        this.maxFilesTruncated = maxFilesTruncated;
    }

    public Path getIndexPathsFile() {
        return indexPathsFile;
    }

    public void setIndexPathsFile(Path indexPathsFile) {
        this.indexPathsFile = indexPathsFile;
    }

    public Path getTruncatedUrlsFile() {
        return truncatedUrlsFile;
    }

    public void setTruncatedUrlsFile(Path truncatedUrlsFile) {
        this.truncatedUrlsFile = truncatedUrlsFile;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public void setRecordSelector(RecordSelector recordSelector) {
        this.recordSelector = recordSelector;
    }

    public void setTargetPathPattern(String pathPattern) {
        this.targetPathPattern = pathPattern;
    }

    public String getTargetPathPattern() {
        return targetPathPattern;
    }

    public TargetPathRewriter getTargetPathRewriter() {
        return new TargetPathRewriter(targetPathPattern);
    }

    public RecordSelector getRecordSelector() {
        return recordSelector;
    }

    public IndexIterator getIndexIterator() {
        return indexIterator;
    }

    public RangeFetcher newFetcher() throws TikaConfigException {
        return fetchConfig.newFetcher();
    }

    public StreamEmitter newEmitter() throws TikaConfigException {
        if (emitConfig == null) {
            emitConfig = new EmitConfig(DEFAULT_FS_DOCS_PATH);
        }
        return emitConfig.newEmitter();
    }

    private static class FetchConfig {
        private final String profile;
        private final long[] throttleSeconds;
        @JsonCreator
        public FetchConfig(@JsonProperty("profile") String profile,
                           @JsonProperty("throttleSeconds") long[] throttleSeconds) {
            this.profile = profile;
            this.throttleSeconds = (throttleSeconds == null) ?
                    DEFAULT_THROTTLE_SECONDS : throttleSeconds;
        }

        RangeFetcher newFetcher() throws TikaConfigException {
            RangeFetcher fetcher;
            if (profile == null) {
                fetcher = new BackoffHttpFetcher(throttleSeconds);
            } else {
                fetcher = new S3Fetcher();
                ((S3Fetcher)fetcher).setProfile(profile);
                ((S3Fetcher)fetcher).setCredentialsProvider("profile");
                ((S3Fetcher)fetcher).setBucket(FetcherConfig.CC_S3_BUCKET);
                ((S3Fetcher)fetcher).setRegion(FetcherConfig.CC_REGION);
            }
            if (fetcher instanceof Initializable) {
                ((Initializable)fetcher).initialize(Collections.EMPTY_MAP);
            }
            return fetcher;
        }
    }

    private static class EmitConfig {
        private String profile;
        private String region;
        private String bucket;
        private String prefix;
        private String path;

        private EmitConfig(String path) {
            this.path = path;
        }

        //TODO -- clean this up with different classes
        //for the different fetchers and use jackson's inference
        @JsonCreator
        public EmitConfig(@JsonProperty("profile") String profile,
                          @JsonProperty("region") String region,
                          @JsonProperty("bucket") String bucket,
                          @JsonProperty("prefix") String prefix,
                          @JsonProperty("path") String path) {
            this.profile = profile;
            this.region = region;
            this.bucket = bucket;
            this.prefix = prefix;
            this.path = path;
        }

        public StreamEmitter newEmitter() throws TikaConfigException {
            if (! StringUtils.isBlank(profile)) {
                S3Emitter emitter = new S3Emitter();
                emitter.setCredentialsProvider("profile");
                emitter.setProfile(profile);

                if (StringUtils.isBlank(bucket)) {
                    throw new TikaConfigException("Must specify a bucket for docs");
                }
                emitter.setBucket(bucket);
                if (region != null) {
                    emitter.setRegion(region);
                } else {
                    emitter.setRegion(FetcherConfig.CC_REGION);
                }
                if (! StringUtils.isBlank(prefix)) {
                    emitter.setPrefix(prefix);
                }
                emitter.initialize(Collections.EMPTY_MAP);
                return emitter;
            }
            if (StringUtils.isBlank(path)) {
                path = DEFAULT_FS_DOCS_PATH;
            }
            FileSystemEmitter emitter = new FileSystemEmitter();
            emitter.setBasePath(path);
            emitter.setOnExists("skip");
            return emitter;
        }
    }
}
