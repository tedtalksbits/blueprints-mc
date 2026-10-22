# Verification and Paper acceptance test

## Automated

Run `./gradlew build` (Windows: `.\gradlew.bat build`) with JDK 25. The JUnit suite uses MockBukkit 26.3 and exercises:

- Exact orientation normalization, ordering, duplicate/bounds validation, door pairing, unsafe-state rejection.
- Full material totals, double-slab cost, all-or-nothing debit, custom-item exclusion.
- Missing-material pause/replenishment, exact existing state acceptance, obstruction handling, paired-door placement.
- Cursor/plan persistence, clean restart continuation, unclean recovery quarantine, malformed/legacy snapshots, failed atomic replacement.
- Cancellation authorization/no refunds, owner mode/permission/height checks, global fairness, unloaded destination checks before access, failure stopping further debits.
- Actual plugin loading and bundled demo compilation.
- Creative capture, duplicate-file protection, unloaded/oversized selections, and the complete capture-to-transformed-construction command flow.
- Coordinate/state transforms, all stair corner/facing reflections, log axes, door hinges, unchanged costs, and persisted transformed plans.

Passing these tests is not a live Paper/Geyser acceptance result. The first implementation pass built the JAR and passed this suite; live server testing is still required.

## Manual acceptance (isolated server)

1. Start a separate Paper 26.3 server with Java 25 and its normal operator-approved EULA/settings. Install the built JAR. Check the log for one loaded blueprint and no enable errors.
2. Prepare a flat test area with air at the intended platform Y. Put a single chest outside the 5x5 footprint and remain close to it in Survival as an operator.
3. Start `orientation-demo` with an **empty chest**. Verify `PAUSED_MATERIALS`, no placed blocks, and no inventory changes.
4. Supply only 5 stone bricks. Verify exactly five platform blocks appear and construction pauses. Supply 20 more stone bricks, 4 stairs, 3 logs, 4 slabs, 1 trapdoor, and 1 door.
5. Verify the 38-block demo finishes. The chest should have consumed exactly those amounts. Inspect the block states with F3 (Java) or `/execute if block <x> <y> <z> <block-state>` checks: four stair directions including west/top, X/Y/Z logs, three slab variants, west/top/open trapdoor, north/right-hinged two-half door. The two door halves must appear together.
6. Repeat at a fresh origin. Fund only part of the build, use the server's normal `/stop`, restart, rejoin in Survival, and supply the rest. Verify the cursor continues and already completed work is not charged again. Edit/delete the source blueprint between the stop and restart; the existing job must retain its original plan.
7. At a fresh origin, place dirt in a future destination. The job must pause before charging that step, preserve the dirt, and continue after you remove it. An existing matching full step should be accepted for free; an existing wrong-facing stair must obstruct. A partially existing door must obstruct.
8. For a blueprint crossing a chunk boundary, arrange for the next destination chunk to be unloaded. Verify `PAUSED_CHUNK` and no implicit chunk load or item debit. Reload it and verify continuation. Likewise test the chest chunk independently.
9. Move farther than 128 blocks away, disconnect, change to Creative, or revoke `blueprints.build`; construction should pause before another paid step. Restore the condition and it should resume.
10. Cancel mid-build. Verify placed blocks remain, unused items stay in the chest, and no refund or further construction occurs. Verify another ordinary player cannot cancel the job.
11. **Disposable test world only:** force-terminate the server during an unfinished job. On restart, verify `RECOVERY_REQUIRED` and no automatic debit/placement. Inspect and reconcile chest/world state before cancelling and starting a new job. Do not expect process-kill recovery to be transactionally lossless.
12. Only after Java/Paper behavior is accepted, test the command/chest workflow via Bedrock/Geyser. No client mod is required for these commands. Verify CoreProtect expectations separately: V1 does not log plugin placements through CoreProtect.

Record the Paper build, Java version, plugin commit, observed counts/states, and any errors. Do not install this first slice directly onto the existing survival server before completing this test and choosing a protection/audit integration policy.

## Capture and transform acceptance

1. In Creative, make a supported small design with an asymmetric outline, a corner stair, an X-axis log, a trapdoor, and a complete door. Keep containers outside the selected cuboid.
2. Run `/blueprint capture <id> <x1> <y1> <z1> <x2> <y2> <z2>` with its corners reversed. Verify the minimum selection corner is the origin, air is skipped, the original design is unchanged, and `/blueprint materials <id>` has the correct totals. A duplicate capture must leave the original file intact.
3. Switch to Survival, fund a chest at an empty destination, and start the capture without transform arguments. Verify the result matches the original design.
4. At fresh origins, test `90 none`, `180 none`, `270 none`, `0 x`, `0 z`, and `90 x`. Leave room for negative local offsets: transforms pivot around the fixed origin. Verify outline geometry, facings, log axes, corner stair handedness, door hinge/halves, slab costs, and trapdoor half/open state.
5. Normally stop and restart during a transformed build. Rejoin in Survival and verify it continues from the stored transformed plan with no extra charge. Editing the original capture while stopped must not affect this job.
6. Verify capture rejects Survival mode, missing capture permission, unloaded chunks, a cuboid over 4,096 cells, a side over 65 blocks, a distant cuboid, an empty selection, containers, and half a door. These cases must not create a usable blueprint or load chunks.
7. Transfer a captured YAML to a separate stopped test server's blueprint directory. Start that server and verify the design can be listed and constructed there without the original Creative world.
