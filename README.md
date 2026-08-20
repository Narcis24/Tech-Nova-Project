### Branching Strategy

As a team, we revisited the trunk-based vs Gitflow-style comparison and discussed our team size, expected merge frequency, and the process overhead appropriate for a 10-week project.

**Decision:** The team has agreed to use a **Gitflow branching strategy**.

We expect to merge work back into the main development branch approximately **once every 3-5 days**, which fits well with Gitflow's structured use of feature, develop, and release branches.

In this scenario, usually teams would work by themselves or in pairs for pair programming. 

Each team member would work on a feature that doesn't conflict with another's work, using their own `feature/` branch created off `develop`. Once a feature is complete, the author opens
a pull request back into `develop`, and at least one other team member reviews and approves the changes before merging. This keeps `develop` stable while still letting people work in parallel.

When the team is ready to prepare a release, a `release/` branch is cut from `develop` for final testing and bug fixes, then merged into both `main` and `develop`. If an urgent fix is needed in production.