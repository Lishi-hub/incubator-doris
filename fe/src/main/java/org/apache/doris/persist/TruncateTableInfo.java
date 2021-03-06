// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.persist;

import org.apache.doris.catalog.Partition;
import org.apache.doris.common.io.Writable;

import com.google.common.collect.Lists;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

public class TruncateTableInfo implements Writable {

    private long dbId;
    private long tblId;
    private List<Partition> partitions = Lists.newArrayList();

    private TruncateTableInfo() {

    }

    public TruncateTableInfo(long dbId, long tblId, List<Partition> partitions) {
        this.dbId = dbId;
        this.tblId = tblId;
        this.partitions = partitions;
    }

    public long getDbId() {
        return dbId;
    }

    public long getTblId() {
        return tblId;
    }

    public List<Partition> getPartitions() {
        return partitions;
    }

    public static TruncateTableInfo read(DataInput in) throws IOException {
        TruncateTableInfo info = new TruncateTableInfo();
        info.readFields(in);
        return info;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeLong(dbId);
        out.writeLong(tblId);
        out.writeInt(partitions.size());
        for (Partition partition : partitions) {
            partition.write(out);
        }
    }

    public void readFields(DataInput in) throws IOException {
        dbId = in.readLong();
        tblId = in.readLong();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            Partition partition = Partition.read(in);
            partitions.add(partition);
        }
    }

}
