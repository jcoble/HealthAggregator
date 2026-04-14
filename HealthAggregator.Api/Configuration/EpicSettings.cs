using HealthAggregator.Core.Models;

namespace HealthAggregator.Api.Configuration;

public sealed class EpicSettings
{
    public const string SectionName = "Epic";

    public string ClientId { get; set; } = "";
    public string FrontendBaseUrl { get; set; } = "https://localhost:5373";
    public string CallbackBaseUrl { get; set; } = "";
    public string CallbackPath { get; set; } = "/api/integrations/epic/callback";
    public List<EpicOrganization> Organizations { get; set; } = [];
}
