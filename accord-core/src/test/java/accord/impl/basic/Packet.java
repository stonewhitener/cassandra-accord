/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package accord.impl.basic;

import accord.impl.mock.Network;
import accord.local.Node.Id;
import accord.messages.Message;
import accord.messages.Reply;
import accord.messages.ReplyContext;
import accord.messages.Request;

public class Packet implements Pending, ReplyContext
{
    static final int SENTINEL_MESSAGE_ID = Integer.MIN_VALUE;

    final Pending origin;
    public final Id src;
    public final Id dst;
    public final long expiresAt;
    public final long requestId; // if message is Reply, this is the id of the message we are replying to
    public final long replyId; // if message is Reply, this is the id of the message we are replying to
    public final Message message;

    public Packet(Id src, Id dst, long expiresAt, long requestId, Request request)
    {
        this(src, dst, expiresAt, requestId, request, src.id < 0);
    }

    public Packet(Id src, Id dst, long expiresAt, long requestId, Request request, boolean isSelfOrigin)
    {
        this.src = src;
        this.dst = dst;
        this.expiresAt = expiresAt;
        this.requestId = requestId;
        this.replyId = SENTINEL_MESSAGE_ID;
        this.message = request;
        this.origin = isSelfOrigin ? this : Pending.Global.activeOrigin();
    }

    public Packet(Id src, Id dst, long expiresAt, long replyId, Reply reply)
    {
        this.src = src;
        this.dst = dst;
        this.expiresAt = expiresAt;
        this.requestId = SENTINEL_MESSAGE_ID;
        this.replyId = replyId;
        this.message = reply;
        this.origin = src.id < 0 ? this : Pending.Global.activeOrigin();
    }

    @Override
    public Pending origin()
    {
        return origin;
    }

    @Override
    public String toString()
    {
        return "{from:" + src + ", "
        + "to:" + dst + ", "
        + (requestId != SENTINEL_MESSAGE_ID ? "id:" + requestId + ", " : "")
        + (replyId != SENTINEL_MESSAGE_ID ? "replyTo:" + replyId + ", " : "")
        + "body:" + message + "}";
    }

    public static long getMessageId(ReplyContext context)
    {
        if (context instanceof Network.MessageId)
            return ((Network.MessageId) context).msgId;
        return ((Packet) context).requestId;
    }

    public static long getExpiresAt(ReplyContext context)
    {
        if (context instanceof Network.MessageId)
            return -1;
        return ((Packet) context).expiresAt;
    }
}
