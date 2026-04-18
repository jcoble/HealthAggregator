using HealthAggregator.Api.Configuration;
using HealthAggregator.Api.Services;
using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/integrations/epic")]
public sealed class EpicIntegrationsController(
    EpicFhirClient epicClient,
    HealthAggregatorDbContext db,
    IOptions<EpicSettings> settings) : ControllerBase
{
    [HttpGet("organizations")]
    public IActionResult GetOrganizations()
    {
        var s = settings.Value;
        return Ok(new
        {
            configured = s.AnyClientIdConfigured,
            clientIds = new
            {
                legacy = !string.IsNullOrWhiteSpace(s.ClientId),
                nonProduction = !string.IsNullOrWhiteSpace(s.NonProductionClientId),
                production = !string.IsNullOrWhiteSpace(s.ProductionClientId)
            },
            organizations = epicClient.GetOrganizations()
        });
    }

    [HttpGet("connect")]
    public async Task<IActionResult> Connect([FromQuery] string organizationId, CancellationToken cancellationToken)
    {
        var result = await epicClient.BuildConnectUrlAsync(organizationId, Request.Scheme, Request.Host.Value ?? "localhost", cancellationToken);
        return result.ReadyToAuthorize ? Ok(result) : BadRequest(result);
    }

    [HttpGet("authorize")]
    public async Task<IActionResult> Authorize([FromQuery] string organizationId, CancellationToken cancellationToken)
    {
        var result = await epicClient.BuildConnectUrlAsync(organizationId, Request.Scheme, Request.Host.Value ?? "localhost", cancellationToken);
        return result.ReadyToAuthorize && result.AuthorizationUrl is not null
            ? Redirect(result.AuthorizationUrl)
            : BadRequest(result);
    }

    [HttpGet("callback")]
    public async Task<IActionResult> Callback([FromQuery] string? code, [FromQuery] string? state, [FromQuery] string? error, CancellationToken cancellationToken)
    {
        if (!string.IsNullOrWhiteSpace(error))
        {
            return Redirect($"{settings.Value.FrontendBaseUrl}/?epic=error&reason={Uri.EscapeDataString(error)}");
        }

        if (string.IsNullOrWhiteSpace(code) || string.IsNullOrWhiteSpace(state))
        {
            return BadRequest("Epic callback requires code and state.");
        }

        try
        {
            await epicClient.CompleteCallbackAsync(code, state, cancellationToken);
            return Redirect($"{settings.Value.FrontendBaseUrl}/?epic=connected");
        }
        catch (Exception ex)
        {
            return Redirect($"{settings.Value.FrontendBaseUrl}/?epic=error&reason={Uri.EscapeDataString(ex.Message)}");
        }
    }

    [HttpGet("connections")]
    public async Task<IActionResult> GetConnections(CancellationToken cancellationToken)
    {
        var connections = await db.EpicConnections
            .AsNoTracking()
            .OrderBy(connection => connection.OrganizationName)
            .Select(connection => new
            {
                connection.Id,
                connection.OrganizationId,
                connection.OrganizationName,
                connection.FhirBaseUrl,
                connection.PatientId,
                connection.ConnectedAt,
                connection.LastSyncedAt,
                HasToken = !string.IsNullOrWhiteSpace(connection.AccessToken)
            })
            .ToListAsync(cancellationToken);

        return Ok(connections);
    }

    [HttpPost("sync")]
    public async Task<IActionResult> Sync([FromQuery] int connectionId, CancellationToken cancellationToken)
    {
        var summary = await epicClient.SyncConnectionAsync(connectionId, cancellationToken);
        return Ok(summary);
    }

    [HttpDelete("connections/{id:int}")]
    public async Task<IActionResult> Disconnect([FromRoute] int id, CancellationToken cancellationToken)
    {
        var connection = await db.EpicConnections.SingleOrDefaultAsync(c => c.Id == id, cancellationToken);
        if (connection is null) return NotFound();
        db.EpicConnections.Remove(connection);
        await db.SaveChangesAsync(cancellationToken);
        return Ok(new { id });
    }
}
