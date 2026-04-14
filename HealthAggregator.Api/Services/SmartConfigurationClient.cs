using System.Collections.Concurrent;
using System.Net.Http.Json;
using System.Text.Json.Serialization;

namespace HealthAggregator.Api.Services;

public sealed class SmartConfigurationClient(HttpClient httpClient)
{
    private readonly ConcurrentDictionary<string, SmartConfiguration> _cache = new();

    public async Task<SmartConfiguration> ResolveAsync(string fhirBaseUrl, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(fhirBaseUrl))
        {
            throw new SmartConfigurationException("FhirBaseUrl is empty; configure it in appsettings before connecting.");
        }

        if (_cache.TryGetValue(fhirBaseUrl, out var cached))
        {
            return cached;
        }

        var url = fhirBaseUrl.TrimEnd('/') + "/.well-known/smart-configuration";
        HttpResponseMessage response;
        try
        {
            response = await httpClient.GetAsync(url, cancellationToken);
        }
        catch (Exception ex)
        {
            throw new SmartConfigurationException($"Network error fetching smart-configuration from {url}: {ex.Message}", ex);
        }

        if (!response.IsSuccessStatusCode)
        {
            throw new SmartConfigurationException($"smart-configuration returned {(int)response.StatusCode} for {url}.");
        }

        SmartConfigurationDocument? doc;
        try
        {
            doc = await response.Content.ReadFromJsonAsync<SmartConfigurationDocument>(cancellationToken);
        }
        catch (Exception ex)
        {
            throw new SmartConfigurationException($"smart-configuration at {url} returned invalid JSON: {ex.Message}", ex);
        }

        if (doc is null || string.IsNullOrWhiteSpace(doc.AuthorizationEndpoint) || string.IsNullOrWhiteSpace(doc.TokenEndpoint))
        {
            throw new SmartConfigurationException($"smart-configuration at {url} is missing authorization_endpoint or token_endpoint.");
        }

        var config = new SmartConfiguration(doc.AuthorizationEndpoint, doc.TokenEndpoint);
        _cache[fhirBaseUrl] = config;
        return config;
    }

    private sealed class SmartConfigurationDocument
    {
        [JsonPropertyName("authorization_endpoint")] public string? AuthorizationEndpoint { get; set; }
        [JsonPropertyName("token_endpoint")] public string? TokenEndpoint { get; set; }
    }
}

public sealed record SmartConfiguration(string AuthorizationEndpoint, string TokenEndpoint);

public sealed class SmartConfigurationException(string message, Exception? inner = null) : Exception(message, inner);
