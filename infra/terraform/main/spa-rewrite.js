// CloudFront Function (viewer request, UI behaviour only): a deep link such as /trade or /orders/42 has no
// file in the bucket, so it gets index.html and Angular's router takes over in the browser.
// Done here rather than with a custom error page, which would also turn real 404s from the API into the app.
function handler(event) {
    var request = event.request;
    var lastSegment = request.uri.split('/').pop();
    // Files (main-ABC.js, favicon.ico, images/logo.svg) are served as they are
    if (lastSegment.indexOf('.') === -1) {
        request.uri = '/index.html';
    }
    return request;
}
