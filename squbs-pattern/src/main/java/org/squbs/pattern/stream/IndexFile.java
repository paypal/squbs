/*
 *  Copyright 2017 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.squbs.pattern.stream;

import net.openhft.chronicle.bytes.MappedFile;
import net.openhft.chronicle.core.OS;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

public class IndexFile extends MappedFile {

    protected IndexFile(@NotNull File file, @NotNull RandomAccessFile raf, long chunkSize, long overlapSize, long capacity) {
        super(file, raf, chunkSize, overlapSize, capacity, false);
    }

    public static IndexFile of(@NotNull File file, long chunkSize, long overlapSize, long capacity)
            throws FileNotFoundException {
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        return new IndexFile(file, raf, chunkSize, overlapSize, capacity);
    }

    public static IndexFile of(@NotNull File file, long chunkSize, long overlapSize) throws FileNotFoundException {
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        return new IndexFile(file, raf, chunkSize, overlapSize, OS.pageSize());
    }

    @NotNull
    public static IndexFile of(@NotNull File file, long chunkSize) throws FileNotFoundException {
        return of(file, chunkSize, OS.pageSize());
    }

    @NotNull
    public static IndexFile of(@NotNull String filename, long chunkSize, long overlapSize) throws FileNotFoundException {
        return of(new File(filename), chunkSize, overlapSize);
    }
}
