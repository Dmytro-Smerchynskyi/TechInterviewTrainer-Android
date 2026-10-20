# Firebase Analytics events

Events are emitted from ViewModel actions, not Compose rendering. Recomposition and
configuration changes do not emit them again. The production implementation is
injected through Hilt; unit tests use a recording implementation.

| Event | When | Parameters |
| --- | --- | --- |
| `ai_evaluation_started` | A validated answer is submitted for evaluation; includes practice and interview answers. Local validation and recovery of an existing evaluation do not count. | `context`: practice/interview, `grade`: JUNIOR/MIDDLE/SENIOR, `input_mode`: voice/text |
| `paywall_opened` | The paywall changes from closed to open. Repeated calls while already open do not count. | None |
| `answer_revealed` | An answer request succeeds for a question whose `answerRevealed` flag was false. | None |
| `answer_opened` | A reference answer is successfully shown in question details, including reopening a cached answer. Hiding or failing to load an answer does not count. | `source`: network/cache |
| `interview_started` | A newly created interview is confirmed by the server. Resuming an existing interview does not count. | `grade`: JUNIOR/MIDDLE/SENIOR |
| `interview_completed` | The server successfully finishes an interview, including finishing early. Viewing an existing summary does not count. | `grade`: JUNIOR/MIDDLE/SENIOR, `completion`: full/partial |

No email, display name, account/session/purchase identifiers, question or answer
text, audio, passwords, tokens, or server error messages are attached.

For parameter breakdowns in Analytics reports, register context, grade,
input_mode, source and completion as event-scoped custom dimensions in Google
Analytics. Events can be inspected in DebugView after enabling Analytics debug
mode for a development device. Delivery to Firebase Console is separate from
the unit tests.
