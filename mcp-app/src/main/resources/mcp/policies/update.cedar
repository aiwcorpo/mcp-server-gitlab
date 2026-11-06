// Changes only inside granted scopes. A grant on GitLab::Group/team-a covers team-a, its subgroups and their projects,
// but not team-ab: resource.scopes holds every path prefix of the group the resource is in, split at "/".
// There is no early scope check at the gateway for these types, because the grant of a group covers paths the argument
// does not spell, so the decision is `principal in resource.scopes` here.

// Creating a project or subgroup is an update of the containing group.
@id("gitlab-update-group-in-scope")
permit (
  principal in UserGroup::"mcp-gitlab-write",
  action == GitLab::Action::"update",
  resource is GitLab::Group
)
when { principal in resource.scopes };

@id("gitlab-update-project-in-scope")
permit (
  principal in UserGroup::"mcp-gitlab-write",
  action == GitLab::Action::"update",
  resource is GitLab::Project
)
when { principal in resource.scopes };

// Deleting a group is not offered to agents: there is no permit for delete on GitLab::Group.
@id("gitlab-delete-project-in-scope")
permit (
  principal in UserGroup::"mcp-gitlab-destructive",
  action == GitLab::Action::"delete",
  resource is GitLab::Project
)
when { principal in resource.scopes };
