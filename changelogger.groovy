import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.AmazonSQSException
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import edu.internet2.middleware.grouperClient.util.GrouperClientConfig

class Config {
    static String  requiredPattern = ":exports:" //"FILTER", "required_stem"

//    static amqpSystem = null
//    public static ActiveMqGrouperExternalSystem getAmqSystem() {
//        if (amqpSystem) {
//            return amqpSystem
//        } else {
//            amqpSystem = new ActiveMqGrouperExternalSystem()
//            amqpSystem.setConfigId("amqp")
//        }
//    }
}

long lastSequenceProcessed = -1;
AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
String QUEUE_URL = GrouperClientConfig.retrieveConfig().propertyValueStringRequired("lafayette.sqsFifo.queueUrl")

for (EsbEventContainer esbEventContainer : gsh_builtin_esbEventContainers) {
    EsbEvent esbEvent = esbEventContainer.getEsbEvent();
    //gsh_builtin_debugMap.put(esbEventContainer.getSequenceNumber() + "_" + esbEvent.getGroupName(), esbEvent.getSourceId() + "_" + esbEvent.getSubjectId());
    //gsh_builtin_hib3GrouperLoaderLog.addInsertCount(1);

    /*
    {
      "action": "add",
      "group": groupName,
      "subject": subjectId
    }

     */
    gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] Testing\n")
    def action_name =  esbEvent.eventType
    gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] action name: '" + action_name + "'\n")
    if (action_name == 'MEMBERSHIP_ADD' || action_name == 'MEMBERSHIP_DELETE') {

        def subjectId = esbEvent.subjectId
        def groupName = esbEvent.groupName

        if (groupName.contains(Config.requiredPattern)) {
            gsh_builtin_debugMap.put(esbEventContainer.sequenceNumber + "_" + groupName, esbEvent.sourceId + "_" + subjectId)
            try {

                String sqsAction = 'unknown'
                if (action_name == 'MEMBERSHIP_ADD') {
                    sqsAction = 'add'
                } else if (action_name == 'MEMBERSHIP_DELETE') {
                    sqsAction = 'delete'
                }

                Map<String, String> pojo = new LinkedHashMap<String, String>()
                pojo.put("action", sqsAction)
                pojo.put("group", groupName)
                pojo.put("subject", subjectId)
                String json = GrouperUtil.jsonConvertTo(pojo)

                SendMessageRequest request = new SendMessageRequest().
                        withQueueUrl(QUEUE_URL).
                        withMessageBody(json).
                        withMessageGroupId(groupName).
                        withMessageDeduplicationId(UUID.randomUUID().toString())
                sqs.sendMessage(request)
            } catch (SocketException e) {
                // TODO try to reconnect
                gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("Connection error sending message: ${e.stackTrace}\n")
            } catch (Exception e) {
                gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("Could not send message: ${e.stackTrace}\n")
                gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] ${e.getMessage()}\n")
                Thread.sleep(10)
            }
        }
    }
    lastSequenceProcessed = esbEventContainer.getSequenceNumber();
}
return lastSequenceProcessed;

