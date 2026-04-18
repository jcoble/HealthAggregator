# Epic customer approval — follow-up cadence

> Template for keeping the approval process moving at Cleveland Clinic and Summa Health. The underlying lever is the **HIPAA right-of-access** (45 CFR 164.524(c)(3)(ii)) plus the **21st Century Cures Act information-blocking rule** (42 U.S.C. 300jj-52). Both legally obligate the organization to engage — not to approve, but to respond.

Track each org in a small text file — date sent, channel, response (or absence), next action.

## Standard cadence

### Day 0 — Initial email

Send `epic-app-approval-playbook.md`'s prepared email. Bcc yourself. Save a copy.

### Day 3 — Courtesy check (optional)

If no auto-ack received, verify it didn't bounce. Reply to your own bcc: "Just confirming delivery — saw no auto-reply, flagging in case it landed in spam." Don't reach out to the org.

### Day 7 — First follow-up email

**Subject:** Re: HIPAA Right-of-Access Request — Following Up (7 days)

> Hello,
>
> Following up on my HIPAA right-of-access request sent on [DATE] (original below). I understand your team is busy — I want to stay on your radar and am happy to provide any additional documentation your vetting process requires.
>
> The current status on my side:
> - Production Client ID is registered on fhir.epic.com and Ready for Production: `[PRODUCTION_CLIENT_ID]`
> - App brief with architecture, scopes, privacy, and data-flow details is ready to send — let me know if you'd like it attached
>
> Please let me know who owns this request and what the next step looks like. Happy to jump on a call.
>
> Regulatory note: this is a patient-directed access request under 45 CFR 164.524(c)(3)(ii). Under ONC's information-blocking rule (42 U.S.C. 300jj-52), covered actors are required to engage with such requests. I'm not asking for special treatment — just for the request to be routed to the team that can evaluate it.
>
> Thanks,
> Jesse Coble
>
> [ORIGINAL EMAIL BELOW, quoted]

### Day 14 — Phone call

Use the playbook's phone script. Specifically ask:

1. "Who is the Epic administrator at your organization?"
2. "Who handles FHIR app registrations / downloads?"
3. "Can you route this ticket to [that team/person] and give me a ticket number or name I can reference?"
4. "Should I follow up directly, or will they reach me?"

**Write everything down.** Name, extension, ticket number, date/time of the call. Email recap immediately: *"As a record of our call today at [TIME] — you said X, the next step is Y, and [NAME] owns the request. Please correct anything I've mis-stated."*

### Day 21 — Escalate to privacy officer

**Subject:** Escalation — HIPAA Right-of-Access Delay (21 days)

> Hello [Privacy Officer],
>
> I am writing to escalate a HIPAA right-of-access request first submitted on [DATE] that has not received a substantive response in 21 days.
>
> The request is for your organization to download the Epic client record for my personal patient-directed PHR application (client_id `[PRODUCTION_CLIENT_ID]`) into your Epic production environment. This is the standard Epic customer process documented at https://fhir.epic.com/Documentation?docId=appapproval.
>
> Under 45 CFR 164.524(b)(2)(i), covered entities have 30 days to act on right-of-access requests (with one 30-day extension available on notification to the patient). We are at day 21 with no substantive engagement. I need a response or a named owner before day 30 to avoid needing to file a complaint with HHS OCR.
>
> Please respond with either:
> - An estimated date of download, or
> - A specific question / documentation request that's blocking progress, or
> - A declination with the specific reason and legal basis
>
> Thank you,
> Jesse Coble
> coble.jesse@gmail.com

### Day 30 — File information-blocking complaint

If no substantive response, file at `https://healthit.gov/feedback` (ONC's information-blocking complaint form). Also consider HHS OCR for the HIPAA violation: `https://ocrportal.hhs.gov/ocr/`.

**Complaint structure:**

- **Alleged covered entity:** [Org]
- **Practice:** Interference with patient-directed access to a certified API. Specifically: refusal or failure to route and act on a documented request for the organization to complete its standard customer-side configuration step (Epic app client download) that is required for the patient to exercise their 45 CFR 164.524(c)(3)(ii) right with a patient-directed third-party app.
- **Factual timeline:** day-by-day log of every outreach attempt
- **Evidence:** all email threads (forwarded), call notes
- **Relief requested:** that the entity complete the download within a specified time, document its process for future patient requests, or explain in writing why the request does not qualify under the right-of-access rule
- **Precedent citation:** fastenhealth/information-blocking-complaints/IB-2820-labcorp.md

Bcc the organization's legal/privacy officer on the submission. Nothing focuses attention like knowing OCR now has a file.

### Day 31+ — Repeat if needed

Some orgs wait until the complaint is filed to move. That's not unusual. Keep the paper trail, reply to all HHS correspondence, and the download often materializes within 2-4 weeks of the complaint.

## What not to do

- **Don't send 10 emails in 10 days.** The HIPAA angle works because it's measured. Escalation requires a credible paper trail of reasonable-interval follow-ups.
- **Don't argue.** If they ask for documentation, send it. If they say "we don't do that", ask for the policy in writing and escalate.
- **Don't accept verbal "we're working on it" indefinitely.** Every call becomes an email recap same day. If it's not in writing, it didn't happen.
- **Don't volunteer MRN until asked.** It invites the ticket to bounce through internal routing before anyone owns it.

## Parallel tracks

Run both orgs' cadence in parallel. If one org approves on day 14 and the other is on day 30, you have half your data flowing while you keep fighting for the other half.

While waiting: keep building Stream B/C/D features against the Epic sandbox. Nothing about the app's internal logic depends on real-org data. When approval lands, you flip the client_id and real records flow.
