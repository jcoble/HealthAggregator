using HealthAggregator.Core.Models;

namespace HealthAggregator.Api.Configuration;

public sealed class EpicSettings
{
    public const string SectionName = "Epic";

    /// <summary>
    /// Legacy / fallback client_id. Used when a more specific ProductionClientId
    /// or NonProductionClientId is not set for the org's environment.
    /// </summary>
    public string ClientId { get; set; } = "";

    /// <summary>
    /// Client_id for Epic's shared sandbox and customer non-production environments.
    /// Used when connecting to an org where IsSandbox == true.
    /// </summary>
    public string NonProductionClientId { get; set; } = "";

    /// <summary>
    /// Client_id for customer production environments (Cleveland Clinic, Summa Health, etc.).
    /// Used when connecting to an org where IsSandbox == false.
    /// </summary>
    public string ProductionClientId { get; set; } = "";

    public string FrontendBaseUrl { get; set; } = "https://localhost:5373";
    public string CallbackBaseUrl { get; set; } = "";
    public string CallbackPath { get; set; } = "/api/integrations/epic/callback";
    public List<EpicOrganization> Organizations { get; set; } = [];

    /// <summary>
    /// Resolves the correct client_id for a given organization. Prefers the
    /// environment-specific id; falls back to the legacy ClientId if the
    /// specific one isn't set. Returns null when neither is available.
    /// </summary>
    public string? ResolveClientIdFor(EpicOrganization organization)
    {
        var specific = organization.IsSandbox ? NonProductionClientId : ProductionClientId;
        if (!string.IsNullOrWhiteSpace(specific)) return specific;
        if (!string.IsNullOrWhiteSpace(ClientId)) return ClientId;
        return null;
    }

    /// <summary>
    /// True when at least one client_id slot is configured. Used by the UI's
    /// top-level "Epic is configured" banner. Specific orgs may still fail to
    /// connect if their environment's client_id is unset — that error surfaces
    /// at click time via EpicConnectResult.MissingClientId.
    /// </summary>
    public bool AnyClientIdConfigured =>
        !string.IsNullOrWhiteSpace(ClientId)
        || !string.IsNullOrWhiteSpace(NonProductionClientId)
        || !string.IsNullOrWhiteSpace(ProductionClientId);
}
