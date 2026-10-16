ECOroute — Claude Code Starter Pack
==================================

1. Open the LOCAL ECOroute repository folder in VS Code.
2. Extract this ZIP into that repository ROOT, retaining the folder names:
     CLAUDE_CODE_MASTER_PROMPT.md
     docs/reference/EcoRoute_Relational_Schema_v3_Implementation_Corrected.docx
     docs/reference/ECOroute_Member_1_DBMS_Workplan_v4_Corrected.docx
     docs/reference/final-eer.png
     docs/reference/er-disposal-lifecycle.jpeg
   Do not replace existing Java/SQL project source files.
3. Open Claude Code in the ECOroute project root (not the ZIP extraction folder).
4. Send Claude Code this instruction:
     Read ./CLAUDE_CODE_MASTER_PROMPT.md in full, inspect all reference
     files under ./docs/reference/, and EXECUTE it end to end in this
     repository. Do not stop after planning; preserve changes and tests.
5. The agent should work on a NEW integration branch; never reset, force-push,
   erase a database, or merge to main without your permission.

The ZIP contains reference materials and a prompt — NOT a finished application.
MySQL/GUI integration can only be confirmed by executing the project locally.
