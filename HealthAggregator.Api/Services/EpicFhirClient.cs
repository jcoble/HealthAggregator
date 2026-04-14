using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using HealthAggregator.Api.Configuration;
using HealthAggregator.Core.Models;
using HealthAggregator.Data;
using HealthAggregator.Data.Entities;
using HealthAggregator.Data.Services;
using Microsoft.AspNetCore.WebUtilities;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace HealthAggregator.Api.Services;

public sealed class EpicFhirClient(
    HttpClient httpClient,
    HealthAggregatorDbContext db,
    FhirImportService importer,
    IOptions<EpicSettings> settings)
{
    private static readonly string[] PatientScopes =
    [
        "launch/patient",
        "patient/Patient.read",
        "patient/Observation.read",
        "patient/DiagnosticReport.read",
        "patient/Condition.read",
        "patient/MedicationRequest.read",
        "patient/AllergyIntolerance.read",
        "patient/Encounter.read",
        "patient/DocumentReference.read",
        "patient/Binary.read",
        "offline_access"
    ];

    private readonly EpicSettings _settings = settings.Value;

    public IReadOnlyList<EpicOrganization> GetOrganizations() => _settings.Organizations;

    public async Task<EpicConnectResult> BuildConnectUrlAsync(string organizationId, string requestScheme, string requestHost, CancellationToken cancellationToken)
    {
        var organization = FindOrganization(organizationId);
        if (string.IsNullOrWhiteSpace(_settings.ClientId))
        {
            return EpicConnectResult.MissingClientId(organization);
        }

        var state = Base64UrlTextEncoder.Encode(RandomNumberGenerator.GetBytes(32));
        var verifier = Base64UrlTextEncoder.Encode(RandomNumberGenerator.GetBytes(64));
        var challenge = Base64UrlTextEncoder.Encode(SHA256.HashData(Encoding.ASCII.GetBytes(verifier)));
        var redirectBaseUrl = string.IsNullOrWhiteSpace(_settings.CallbackBaseUrl)
            ? $"{requestScheme}://{requestHost}"
            : _settings.CallbackBaseUrl.TrimEnd('/');
        var redirectUri = $"{redirectBaseUrl}{_settings.CallbackPath}";

        db.EpicAuthorizationStates.Add(new EpicAuthorizationState
        {
            State = state,
            OrganizationId = organization.Id,
            CodeVerifier = verifier,
            RedirectUri = redirectUri,
            CreatedAt = DateTimeOffset.UtcNow,
            ExpiresAt = DateTimeOffset.UtcNow.AddMinutes(10)
        });
        await db.SaveChangesAsync(cancellationToken);

        var query = new Dictionary<string, string?>
        {
            ["response_type"] = "code",
            ["client_id"] = _settings.ClientId,
            ["redirect_uri"] = redirectUri,
            ["scope"] = string.Join(' ', PatientScopes),
            ["state"] = state,
            ["aud"] = organization.FhirBaseUrl,
            ["code_challenge"] = challenge,
            ["code_challenge_method"] = "S256"
        };

        return EpicConnectResult.Ready(organization, QueryHelpers.AddQueryString(organization.AuthorizationEndpoint, query));
    }

    public async Task<EpicConnection> CompleteCallbackAsync(string code, string state, CancellationToken cancellationToken)
    {
        var authState = await db.EpicAuthorizationStates.SingleOrDefaultAsync(candidate => candidate.State == state, cancellationToken)
            ?? throw new InvalidOperationException("Epic authorization state was not found or has expired.");

        if (authState.Consumed || authState.ExpiresAt < DateTimeOffset.UtcNow)
        {
            throw new InvalidOperationException("Epic authorization state was already used or has expired.");
        }

        var organization = FindOrganization(authState.OrganizationId);
        using var response = await httpClient.PostAsync(organization.TokenEndpoint, new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["grant_type"] = "authorization_code",
            ["code"] = code,
            ["redirect_uri"] = authState.RedirectUri,
            ["client_id"] = _settings.ClientId,
            ["code_verifier"] = authState.CodeVerifier
        }), cancellationToken);

        var body = await response.Content.ReadAsStringAsync(cancellationToken);
        if (!response.IsSuccessStatusCode)
        {
            throw new InvalidOperationException($"Epic token exchange failed with {(int)response.StatusCode}: {body}");
        }

        using var tokenJson = JsonDocument.Parse(body);
        var root = tokenJson.RootElement;
        var patientId = ReadString(root, "patient");
        var connection = await db.EpicConnections.SingleOrDefaultAsync(candidate => candidate.OrganizationId == organization.Id, cancellationToken);

        if (connection is null)
        {
            connection = new EpicConnection
            {
                OrganizationId = organization.Id,
                ConnectedAt = DateTimeOffset.UtcNow
            };
            db.EpicConnections.Add(connection);
        }

        connection.OrganizationName = organization.Name;
        connection.FhirBaseUrl = organization.FhirBaseUrl;
        connection.PatientId = patientId;
        connection.AccessToken = ReadString(root, "access_token");
        connection.RefreshToken = ReadString(root, "refresh_token");
        connection.Scope = ReadString(root, "scope") ?? "";
        authState.Consumed = true;

        await db.SaveChangesAsync(cancellationToken);
        return connection;
    }

    public async Task<EpicSyncSummary> SyncConnectionAsync(int connectionId, CancellationToken cancellationToken)
    {
        var connection = await db.EpicConnections.SingleOrDefaultAsync(candidate => candidate.Id == connectionId, cancellationToken)
            ?? throw new InvalidOperationException("Epic connection was not found.");

        if (string.IsNullOrWhiteSpace(connection.AccessToken) || string.IsNullOrWhiteSpace(connection.PatientId))
        {
            throw new InvalidOperationException("Epic connection is missing an access token or patient id.");
        }

        var resources = new List<FhirResourceSyncSummary>();

        resources.Add(await ImportUrlAsync(connection, $"Patient/{Uri.EscapeDataString(connection.PatientId)}", cancellationToken));

        foreach (var resourcePath in PatientSearchPaths(connection.PatientId))
        {
            resources.Add(await ImportUrlAsync(connection, resourcePath, cancellationToken));
        }

        connection.LastSyncedAt = DateTimeOffset.UtcNow;
        await db.SaveChangesAsync(cancellationToken);
        return EpicSyncSummary.FromResources(resources);
    }

    private async Task<FhirResourceSyncSummary> ImportUrlAsync(EpicConnection connection, string resourcePath, CancellationToken cancellationToken)
    {
        var currentUrl = BuildFhirUrl(connection.FhirBaseUrl, resourcePath);
        var aggregate = ImportSummary.Empty;

        while (!string.IsNullOrWhiteSpace(currentUrl))
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, currentUrl);
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", connection.AccessToken);
            request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/fhir+json"));

            using var response = await httpClient.SendAsync(request, cancellationToken);
            var payload = await response.Content.ReadAsStringAsync(cancellationToken);
            if (!response.IsSuccessStatusCode)
            {
                throw new InvalidOperationException($"FHIR sync failed for {resourcePath} with {(int)response.StatusCode}: {payload}");
            }

            aggregate = ImportSummaryExtensions.Add(aggregate, await importer.ImportBundleAsync(connection.OrganizationId, connection.OrganizationName, payload, cancellationToken));
            currentUrl = ReadNextLink(payload);
        }

        return new FhirResourceSyncSummary(ResourceTypeForPath(resourcePath), resourcePath, aggregate);
    }

    private EpicOrganization FindOrganization(string organizationId) =>
        _settings.Organizations.SingleOrDefault(organization => organization.Id == organizationId)
        ?? throw new InvalidOperationException($"Unknown Epic organization '{organizationId}'.");

    private static IEnumerable<string> PatientSearchPaths(string patientId)
    {
        var encoded = Uri.EscapeDataString(patientId);
        yield return $"Observation?patient={encoded}&category=laboratory&_count=100";
        yield return $"DiagnosticReport?patient={encoded}&_count=100";
        yield return $"Condition?patient={encoded}&_count=100";
        yield return $"MedicationRequest?patient={encoded}&_count=100";
        yield return $"AllergyIntolerance?patient={encoded}&_count=100";
        yield return $"Encounter?patient={encoded}&_count=100";
        yield return $"DocumentReference?patient={encoded}&_count=100";
    }

    private static string ResourceTypeForPath(string resourcePath)
    {
        var end = resourcePath.IndexOfAny(['?', '/']);
        return end > 0 ? resourcePath[..end] : resourcePath;
    }

    private static string BuildFhirUrl(string fhirBaseUrl, string resourcePath) =>
        $"{fhirBaseUrl.TrimEnd('/')}/{resourcePath.TrimStart('/')}";

    private static string? ReadNextLink(string payload)
    {
        using var document = JsonDocument.Parse(payload);
        if (!document.RootElement.TryGetProperty("link", out var links) || links.ValueKind != JsonValueKind.Array)
        {
            return null;
        }

        foreach (var link in links.EnumerateArray())
        {
            if (string.Equals(ReadString(link, "relation"), "next", StringComparison.OrdinalIgnoreCase))
            {
                return ReadString(link, "url");
            }
        }

        return null;
    }

    private static string? ReadString(JsonElement element, string propertyName)
    {
        return element.TryGetProperty(propertyName, out var value) && value.ValueKind == JsonValueKind.String
            ? value.GetString()
            : null;
    }
}

public sealed record EpicConnectResult(EpicOrganization Organization, string? AuthorizationUrl, string? Error)
{
    public bool ReadyToAuthorize => AuthorizationUrl is not null;

    public static EpicConnectResult Ready(EpicOrganization organization, string authorizationUrl) =>
        new(organization, authorizationUrl, null);

    public static EpicConnectResult MissingClientId(EpicOrganization organization) =>
        new(organization, null, "Set Epic:ClientId before starting the SMART on FHIR authorization flow.");
}

public static class ImportSummaryExtensions
{
    public static ImportSummary Add(ImportSummary left, ImportSummary right) =>
        new(
            left.SourceRecordsUpserted + right.SourceRecordsUpserted,
            left.LabObservationsUpserted + right.LabObservationsUpserted,
            left.DiagnosticReportsUpserted + right.DiagnosticReportsUpserted,
            left.PatientsUpserted + right.PatientsUpserted,
            left.ConditionsUpserted + right.ConditionsUpserted,
            left.MedicationsUpserted + right.MedicationsUpserted,
            left.AllergiesUpserted + right.AllergiesUpserted,
            left.EncountersUpserted + right.EncountersUpserted,
            left.DocumentsUpserted + right.DocumentsUpserted);
}
