/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.quarkiverse.cxf.it.ws.rm.client;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ListIterator;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.InterceptorChain;
import org.apache.cxf.interceptor.MessageSenderInterceptor;
import org.apache.cxf.io.AbstractWrappedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.phase.PhaseInterceptor;
import org.apache.cxf.ws.addressing.AddressingProperties;
import org.apache.cxf.ws.rm.RMContextUtils;
import org.apache.cxf.ws.rm.RMMessageConstants;
import org.jboss.logging.Logger;

/**
 * Makes every second message get lost.
 */
public class MessageLossSimulator extends AbstractPhaseInterceptor<Message> {
    private static final Logger LOG = Logger.getLogger(MessageLossSimulator.class);
    private int appMessageCount;

    public MessageLossSimulator() {
        super(Phase.PREPARE_SEND);
        addBefore(MessageSenderInterceptor.class.getName());
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        AddressingProperties maps = RMContextUtils.retrieveMAPs(message, false, true);
        String action = null;
        if (maps != null && null != maps.getAction()) {
            action = maps.getAction().getValue();
        }
        if (RMContextUtils.isRMProtocolMessage(action)) {
            LOG.info("MessageLossSimulator ignores a RM protocol message");
            return;
        }
        if (MessageUtils.isPartialResponse(message)) {
            LOG.info("MessageLossSimulator ignores a partial response");
            return;
        }
        if (Boolean.TRUE.equals(message.get(RMMessageConstants.RM_RETRANSMISSION))) {
            LOG.info("MessageLossSimulator ignores a RM retransmission");
            return;
        }

        // alternatively lose
        synchronized (this) {
            appMessageCount++;
            if (0 != (appMessageCount % 2)) {
                LOG.info("MessageLossSimulator ignores an odd message");
                return;
            }
        }

        LOG.info("MessageLossSimulator makes an even message " + appMessageCount + " to get lost");
        InterceptorChain chain = message.getInterceptorChain();
        ListIterator<Interceptor<? extends Message>> it = chain.getIterator();
        while (it.hasNext()) {
            PhaseInterceptor<?> pi = (PhaseInterceptor<? extends Message>) it.next();
            if (MessageSenderInterceptor.class.getName().equals(pi.getId())) {
                chain.remove(pi);
                LOG.debug("Removed MessageSenderInterceptor from interceptor chain.");
                break;
            }
        }

        message.setContent(OutputStream.class, new WrappedOutputStream(message));

        message.getInterceptorChain().add(new MessageLossEndingInterceptor(false));
    }

    /**
     * Ending interceptor to discard message output. Note that the name is used as a String by RMCaptureOutInterceptor,
     * so if ever changed here also needs to be changed there.
     */
    public static final class MessageLossEndingInterceptor extends AbstractPhaseInterceptor<Message> {

        private final boolean throwsException;

        public MessageLossEndingInterceptor(boolean except) {
            super(Phase.PREPARE_SEND_ENDING);
            throwsException = except;
        }

        @Override
        public void handleMessage(Message message) throws Fault {
            try {
                message.getContent(OutputStream.class).close();
                if (throwsException) {
                    throw new IOException("simulated transmission exception");
                }
            } catch (IOException e) {
                throw new Fault(e);
            }
        }
    }

    private static class WrappedOutputStream extends AbstractWrappedOutputStream {

        private Message outMessage;

        WrappedOutputStream(Message m) {
            this.outMessage = m;
        }

        @Override
        protected void onFirstWrite() throws IOException {
            Long nr = RMContextUtils.retrieveRMProperties(outMessage, true)
                    .getSequence().getMessageNumber();
            LOG.info("Losing message " + nr);
            wrappedStream = new DummyOutputStream();
        }
    }

    private static final class DummyOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {

        }

    }

}
