<!-- Sections marked with * are required. Delete the example comments before submitting. -->

## Summary *

<!-- What does this PR change, in one or two sentences?
Example: Adds the 503 SERVICE_UNAVAILABLE response to POST /drivers when Firestore does not respond, and logs the failure at ERROR level. -->

## Related work *

<!-- HU, DEV or TEC id and the issue/backlog item, if any.
Example:
- HU-01 (acceptance criterion 6)
- Closes #12 -->

- Closes #

## Type of change *

<!-- Check every type that applies.
Example: a new endpoint with its tests checks "feat" and "test". -->

- [ ] feat: A new feature.
- [ ] fix: A bug was fixed.
- [ ] docs: Changes were made to the documentation.
- [ ] style: Formatting was applied, missing commas and periods were added, etc; No code changes.
- [ ] refactor: Refactoring of the production code.
- [ ] test: Tests were added, test refactoring; No code changes.
- [ ] chore: Updates to build tasks, package manager configuration; No code changes.

## How it was tested *

<!-- Commands you ran and what you checked (curl, Swagger, new tests).
Example:
- New unit test RegisterDriverServiceTest.returns503WhenFirestoreIsDown
- Stopped the Firestore emulator and ran:
  curl -i -X POST http://localhost:8085/drivers -H "Content-Type: application/json" -d '{...}'
  -> 503 with code SERVICE_UNAVAILABLE -->

- [ ] `./gradlew clean build` passes locally (Checkstyle, tests, coverage)
- [ ] New or changed behavior is covered by tests


## Notes for the reviewer

<!-- Optional. Known limitations, pending items, or anything that needs special attention.
Example: The 404 for a non-UUID id is not part of this PR; it stays for HU-02. Removed the "VALIDATION_ERROR" known limitation from the README. -->
