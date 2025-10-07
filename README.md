# Multi_Agent Job Application
# Right now the projects are two parted
# The backend agent platform is in Python under Agent Platform
# The parsing and db building is in Java under java
# please refer to the README.md under those two directories
# AI usage: Write prompts with AIs by giving my description of what I want to workflow to do
# Before:

Determine the user's intent and respond with JSON:
{{
    "action": "approve|reject|redo_resume|redo_cover|show_resume|show_cover|quit|feedback",
    "feedback": "specific feedback to pass to agent (if action is feedback or redo)",
    "explanation": "brief explanation of what the user wants"
}}

Examples:
- "looks good" → {{"action": "approve", "feedback": "", "explanation": "User approves"}}
- "skip this job" → {{"action": "reject", "feedback": "", "explanation": "User wants to skip"}}
- "I haven't worked for ACME" → {{"action": "redo_resume", "feedback": "Remove experience section claiming work at ACME. Only include actual past experience.", "explanation": "User caught fabrication"}}
- "improve the resume" → {{"action": "redo_resume", "feedback": "Make improvements", "explanation": "User wants better resume"}}
- "show me resume" → {{"action": "show_resume", "feedback": "", "explanation": "User wants to see resume"}}
"""
# After:
 "RULES:
1. Auto-progress: JD → RESUME → APPLY (no HITL)
2. When all 3 done (jd_summary, rendered_resume, cover_letter) AND not yet approved → route to HITL
3. **APPROVAL SIGNALS**: If user says "looks good", "approve", "proceed", "continue", "yes", "apply", "submit" → SET approve_job=true AND route to APPLY
4. **REJECTION SIGNALS**: If user says "skip", "reject", "no", "next job" → SET approve_job=false
5. **REFINEMENT**: If user mentions problems or wants changes → extract feedback, clear relevant artifacts, route to fix
6. If user asks to "show" something → route to HITL (it will display)
