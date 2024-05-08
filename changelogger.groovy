import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.AmazonSQSException
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import edu.internet2.middleware.grouperClient.util.GrouperClientConfig
import edu.internet2.middleware.grouper.*

long lastSequenceProcessed = -1;
AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
String QUEUE_URL = GrouperClientConfig.retrieveConfig().propertyValueStringRequired("lafayette.sqsFifo.queueUrl")

for (EsbEventContainer esbEventContainer : gsh_builtin_esbEventContainers) {
    EsbEvent esbEvent = esbEventContainer.getEsbEvent();
    gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] Testing\n")
    def action_name =  esbEvent.eventType
    def attrib_def_name = "etc:attribute:myAttributes:myExportToFifoMark"
    gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] action name: '" + action_name + "'\n")
    if (action_name == 'MEMBERSHIP_ADD' || action_name == 'MEMBERSHIP_DELETE') {

        gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] Action type meets criteria.\n")
        def subjectId = esbEvent.subjectId
        def groupName = esbEvent.groupName
        gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] subject ID: " + subjectId + "\n")
        gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] group name: " + groupName + "\n")

        Group group = new GroupFinder().addGroupName(groupName).findGroup()
        stem = group.parentStem
        attribs = stem.attributeDelegate.retrieveAttributes()
        //stem_attrib_count = attribs.size()
        //gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] stem_attrib_count: " + stem_attrib_count + "\n")
        stem_has_mark = attribs.any { adn ->
            adn.name == attrib_def_name
        }
        attribs = group.attributeDelegate.retrieveAttributes()
        //group_attrib_count = attribs.size()
        //gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] group_attrib_count: " + group_attrib_count + "\n")
        group_has_mark = attribs.any { adn ->
            adn.name == attrib_def_name
        }
        //gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] stem_has_mark: " + stem_has_mark + "\n")
        //gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] group_has_mark: " + group_has_mark + "\n")

        if (stem_has_mark || group_has_mark) {
            if (stem_has_mark) {
                gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] stem has export mark.\n")
            }
            if (group_has_mark) {
                gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] group has export mark.\n")
            }
            gsh_builtin_debugMap.put(esbEventContainer.sequenceNumber + "_" + groupName, esbEvent.sourceId + "_" + subjectId)
            gsh_builtin_hib3GrouperLoaderLog.appendJobMessage("[XYZZY] Sequence number: " + esbEventContainer.sequenceNumber + ".\n")
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

                gsh_builtin_hib3GrouperLoaderLog.appendJobMessage(
                    "[XYZZY] Sending message- action: " + sqsAction 
                    + ", group: " + groupName 
                    + ", subject:" + subjectId + ".\n")
                SendMessageRequest request = new SendMessageRequest().
                        withQueueUrl(QUEUE_URL).
                        withMessageBody(json).
                        withMessageGroupId(groupName).
                        withMessageDeduplicationId(UUID.randomUUID().toString())
                sqs.sendMessage(request)
                gsh_builtin_hib3GrouperLoaderLog.appendJobMessage(
                    "[XYZZY] Sent message- action: " + sqsAction 
                    + ", group: " + groupName 
                    + ", subject:" + subjectId + ".\n")
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

