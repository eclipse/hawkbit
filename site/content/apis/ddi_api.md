---
title: Direct Device Integration API
parent: APIs
weight: 82
---

The hawkBit [update server](https://github.com/eclipse/hawkbit) provides REST resources which are consumed by the device to retrieve software update tasks.
This API is based on HTTP standards and a polling mechanism.
<!--more-->

{{% note %}}
In DDI the target is identified using a  **controllerId**. Controller is used as a term for the actual service/client on the device. That allows users to have in some cases even multiple clients on the same target for different tasks, e.g. Firmware update and App management.
{{% /note %}}

## State Machine Mapping

For historical reasons the DDI has a different state machine and status messages than the [Target State Machine](../../concepts/targetstate/) of the hawkBit update server.

This is kept in order to ensure that _DDI_ stays compatible for devices out there in the field. A future version "2" of _DDI_ might change that. _DDI_ also defines more states than the update server, e.g. multiple DDI states are currently mapped by the _DDI_ implementation to _RUNNING_ state. It is possible that in the future hawkBit will fully leverage these additional states.

The _DDI_ API allows the device to provide the following feedback messages:

DDI `status.execution` type | handling by update server                                                                                                                                                                                                                | Mapped ActionStatus type
--------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -----------------------------------------------------
CANCELED                    | This is send by the target as confirmation of a cancellation request by the update server.                                                                                                                                                | CANCELED
REJECTED                    | This is send by the target in case an update of a cancellation is rejected, i.e. cannot be fulfilled at this point in time. Note: the target should send a CLOSED->ERROR if it believes it will not be able to proceed the action at all. | WARNING
CLOSED                      | Target completes the action either with `status.result.finished` SUCCESS or FAILURE as result. Note: DDI defines also a status NONE which will not be interpreted by the update server and handled like SUCCESS.                         | ERROR (DDI FAILURE) or FINISHED (DDI SUCCESS or NONE)
DOWNLOAD                    | This can be used by the target to inform that it is downloading artifacts of the action.                                                                                                                                                    | DOWNLOAD
DOWNLOADED                  | This can be used by the target to inform that it has downloaded artifacts of the action.                                                                                                                                                 | DOWNLOADED
PROCEEDING                  | This can be used by the target to inform that it is working on the action.                                                                                                                                                               | RUNNING
SCHEDULED                   | This can be used by the target to inform that it scheduled on the action.                                                                                                                                                                | RUNNING
RESUMED                     | This can be used by the target to inform that it continued to work on the action.                                                                                                                                                        | RUNNING

See this [issue](https://github.com/eclipse/hawkbit/issues/952) for additional information, concerning the cancellation of updates. To finally accept a cancellation, you must send a "closed" status.exection type. 
## DDI APIs

<iframe style="padding-top: 20px;" width="100%" height="900px" frameborder="0" src="../../rest-api/ddi.html"></iframe>
