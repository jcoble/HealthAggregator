using HealthAggregator.Api.Configuration;
using HealthAggregator.Core.Models;

namespace HealthAggregator.Tests;

public sealed class EpicSettingsTests
{
    private static EpicOrganization Sandbox() =>
        new("epic-sandbox", "Epic Sandbox", "https://...", IsSandbox: true);

    private static EpicOrganization Production() =>
        new("cleveland-clinic", "Cleveland Clinic", "https://...", IsSandbox: false);

    [Fact]
    public void ResolveClientIdFor_sandbox_prefers_NonProductionClientId()
    {
        var settings = new EpicSettings
        {
            ClientId = "legacy",
            NonProductionClientId = "nonprod",
            ProductionClientId = "prod"
        };
        Assert.Equal("nonprod", settings.ResolveClientIdFor(Sandbox()));
    }

    [Fact]
    public void ResolveClientIdFor_production_prefers_ProductionClientId()
    {
        var settings = new EpicSettings
        {
            ClientId = "legacy",
            NonProductionClientId = "nonprod",
            ProductionClientId = "prod"
        };
        Assert.Equal("prod", settings.ResolveClientIdFor(Production()));
    }

    [Fact]
    public void ResolveClientIdFor_falls_back_to_legacy_ClientId_when_specific_is_missing()
    {
        var settings = new EpicSettings { ClientId = "legacy" };
        Assert.Equal("legacy", settings.ResolveClientIdFor(Sandbox()));
        Assert.Equal("legacy", settings.ResolveClientIdFor(Production()));
    }

    [Fact]
    public void ResolveClientIdFor_returns_null_when_nothing_set()
    {
        var settings = new EpicSettings();
        Assert.Null(settings.ResolveClientIdFor(Sandbox()));
        Assert.Null(settings.ResolveClientIdFor(Production()));
    }

    [Fact]
    public void ResolveClientIdFor_sandbox_falls_back_when_only_production_is_set()
    {
        // A user who only has ProductionClientId should NOT get it used for a sandbox
        // connection — neither specific matches sandbox, and there's no legacy fallback,
        // so it should return null (surfacing MissingClientId at the UI layer).
        var settings = new EpicSettings { ProductionClientId = "prod" };
        Assert.Null(settings.ResolveClientIdFor(Sandbox()));
    }

    [Fact]
    public void AnyClientIdConfigured_is_false_for_empty_settings()
    {
        var settings = new EpicSettings();
        Assert.False(settings.AnyClientIdConfigured);
    }

    [Fact]
    public void AnyClientIdConfigured_is_true_for_any_slot()
    {
        Assert.True(new EpicSettings { ClientId = "x" }.AnyClientIdConfigured);
        Assert.True(new EpicSettings { NonProductionClientId = "x" }.AnyClientIdConfigured);
        Assert.True(new EpicSettings { ProductionClientId = "x" }.AnyClientIdConfigured);
    }

    [Fact]
    public void AnyClientIdConfigured_ignores_whitespace_only_values()
    {
        var settings = new EpicSettings
        {
            ClientId = "   ",
            NonProductionClientId = "",
            ProductionClientId = ""
        };
        Assert.False(settings.AnyClientIdConfigured);
    }
}
