using HealthAggregator.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/assistant")]
public sealed class AssistantController(ReadOnlyAssistantService assistant) : ControllerBase
{
    [HttpPost("messages")]
    public async Task<IActionResult> Message([FromBody] AssistantMessageRequest request, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Message))
        {
            return BadRequest("Message is required.");
        }

        var reply = await assistant.ReplyAsync(request.Message, cancellationToken);
        return Ok(reply);
    }
}

public sealed record AssistantMessageRequest(string Message);
