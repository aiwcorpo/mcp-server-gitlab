// The platform generates the forbid for an own classification of a group or project and for a restricted parent group of a
// project. What it cannot know is inheritedClassification, an ordinary attribute of this server: the union of the
// classification of every ancestor group, so a restricted group closes its whole subtree, however deep.
// forbid overrides every permit, so no group, scope or link opens it.

@id("gitlab-forbid-restricted-group")
forbid (
  principal,
  action,
  resource is GitLab::Group
)
when { resource.inheritedClassification.contains("restricted") };

@id("gitlab-forbid-restricted-project")
forbid (
  principal,
  action,
  resource is GitLab::Project
)
when { resource.inheritedClassification.contains("restricted") };
