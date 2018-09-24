package no.nav.syfo

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants
import com.ibm.msg.client.wmq.compat.base.internal.MQC

fun connectionFactory(env: Environment) = MQConnectionFactory().apply {
    hostName = env.mqHostname
    port = env.mqPort
    queueManager = env.mqQueueManagerName
    transportType = WMQConstants.WMQ_CM_CLIENT
    channel = env.mqChannelName
    ccsid = 1208
    setIntProperty(WMQConstants.JMS_IBM_ENCODING, MQC.MQENC_NATIVE)
    setIntProperty(WMQConstants.JMS_IBM_CHARACTER_SET, 1208)
}
