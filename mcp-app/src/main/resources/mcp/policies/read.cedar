// Reading follows the user's own rights in GitLab (the server calls GitLab with the user's credential). The group check
// is here; restricted groups and projects are closed by the forbids the platform generates from the classification of
// the types and by restricted.cedar.

@id("gitlab-read")
permit (
  principal in UserGroup::"mcp-gitlab-read",
  action == GitLab::Action::"read",
  resource
)
when { resource is GitLab::Group || resource is GitLab::Project };
