using System.Net;
using HealthAggregator.Api.Services;

namespace HealthAggregator.Tests;

public sealed class SmartConfigurationClientTests
{
    [Fact]
    public async Task ResolveAsync_returns_endpoints_from_well_known_document()
    {
        var handler = new FakeHandler(HttpStatusCode.OK, """
            {
              "authorization_endpoint": "https://fhir.example.org/oauth2/authorize",
              "token_endpoint": "https://fhir.example.org/oauth2/token",
              "code_challenge_methods_supported": ["S256"]
            }
            """);
        var http = new HttpClient(handler);
        var client = new SmartConfigurationClient(http);

        var config = await client.ResolveAsync("https://fhir.example.org/FHIR/R4", CancellationToken.None);

        Assert.Equal("https://fhir.example.org/oauth2/authorize", config.AuthorizationEndpoint);
        Assert.Equal("https://fhir.example.org/oauth2/token", config.TokenEndpoint);
    }

    [Fact]
    public async Task ResolveAsync_caches_per_fhir_base_url()
    {
        var handler = new FakeHandler(HttpStatusCode.OK, """
            {"authorization_endpoint":"a","token_endpoint":"t"}
            """);
        var client = new SmartConfigurationClient(new HttpClient(handler));

        await client.ResolveAsync("https://fhir.x/R4", CancellationToken.None);
        await client.ResolveAsync("https://fhir.x/R4", CancellationToken.None);

        Assert.Equal(1, handler.RequestCount);
    }

    [Fact]
    public async Task ResolveAsync_throws_on_non_200()
    {
        var handler = new FakeHandler(HttpStatusCode.NotFound, "not found");
        var client = new SmartConfigurationClient(new HttpClient(handler));

        await Assert.ThrowsAsync<SmartConfigurationException>(() =>
            client.ResolveAsync("https://fhir.x/R4", CancellationToken.None));
    }

    [Fact]
    public async Task ResolveAsync_throws_when_fields_missing()
    {
        var handler = new FakeHandler(HttpStatusCode.OK, """
            {"authorization_endpoint":"a"}
            """);
        var client = new SmartConfigurationClient(new HttpClient(handler));

        await Assert.ThrowsAsync<SmartConfigurationException>(() =>
            client.ResolveAsync("https://fhir.x/R4", CancellationToken.None));
    }

    [Fact]
    public async Task ResolveAsync_throws_on_empty_base_url()
    {
        var client = new SmartConfigurationClient(new HttpClient(new FakeHandler(HttpStatusCode.OK, "{}")));
        await Assert.ThrowsAsync<SmartConfigurationException>(() =>
            client.ResolveAsync("", CancellationToken.None));
    }

    private sealed class FakeHandler(HttpStatusCode status, string body) : HttpMessageHandler
    {
        public int RequestCount { get; private set; }
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken ct)
        {
            RequestCount++;
            return Task.FromResult(new HttpResponseMessage(status) { Content = new StringContent(body) });
        }
    }
}
