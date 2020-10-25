package au.com.bournedigital.onpremiseproxybridge;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.cloudfoundry.identity.client.UaaContext;
import org.cloudfoundry.identity.client.UaaContextFactory;
import org.cloudfoundry.identity.client.token.GrantType;
import org.cloudfoundry.identity.client.token.TokenRequest;
import org.cloudfoundry.identity.uaa.oauth.token.CompositeAccessToken;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    @RequestMapping(value = "/**", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public String callOnPremForGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException, URISyntaxException, JSONException {
        try {
            String userToken = req.getHeader("Authorization");
            if (StringUtils.isEmpty(userToken)) {
                res.sendError(401, "Authorization header must be present in request");
                throw new Exception();
            }
            // any request after / will be forwarded to SAP On Prem Service
            String requestPath = req.getRequestURI();
            // On Premise destination name
            String destToConsume = "SAP_Cloud_Gateway";
            // This Java app must have binding with XSUAA, Connectivity and Destination
            // Service
            JSONObject vcapServices = new JSONObject(System.getenv("VCAP_SERVICES"));
            JSONArray connectivityService = vcapServices.getJSONArray("connectivity");
            JSONArray xsuaaService = vcapServices.getJSONArray("xsuaa");
            JSONArray destinationService = vcapServices.getJSONArray("destination");
            JSONObject connCreds = connectivityService.getJSONObject(0).getJSONObject("credentials");
            JSONObject xsuaaCreds = xsuaaService.getJSONObject(0).getJSONObject("credentials");
            JSONObject destSrvCreds = destinationService.getJSONObject(0).getJSONObject("credentials");
            String destSrvToken = getAccessToken(xsuaaCreds.getString("url"), destSrvCreds.getString("clientid"),
                    destSrvCreds.getString("clientsecret"));
            JSONObject destinationDetails = getDestinationDetails(destToConsume, destSrvCreds, destSrvToken);
            JSONObject destinationConfig = destinationDetails.getJSONObject("destinationConfiguration");

            String sapClient = destinationConfig.getString("sap-client");
            if (sapClient != null && !sapClient.isEmpty()) {
                if (requestPath.contains("?")) {
                    requestPath += "&";
                } else {
                    requestPath += "?";
                }
                requestPath += "sap-client=" + sapClient;
            }

            String connProxyHost = connCreds.getString("onpremise_proxy_host");
            int connProxyPort = Integer.parseInt(connCreds.getString("onpremise_proxy_http_port"));
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(connProxyHost, connProxyPort));
            String endpoint = destinationConfig.getString("URL");
            URL url = new URL(endpoint + requestPath);
            System.out.println("URL " + url.toString());

            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection(proxy);
            urlConnection.setRequestMethod("GET");
            if (req.getHeader("Accept") != null)
                urlConnection.setRequestProperty("Accept", req.getHeader("Accept"));
            urlConnection.setRequestProperty("Content-Type", req.getContentType());
            String connectivitySrvToken = getAccessToken(xsuaaCreds.getString("url"), connCreds.getString("clientid"),
                    connCreds.getString("clientsecret"));
            urlConnection.setRequestProperty("Proxy-Authorization", connectivitySrvToken);
            urlConnection.setRequestProperty("SAP-Connectivity-Authentication", userToken);
            urlConnection.setUseCaches(false);
            urlConnection.connect();
            InputStream instream = urlConnection.getInputStream();
            byte[] returnedOData = IOUtils.toByteArray(instream);
            instream.close();
            String feedContent = new String(returnedOData, "UTF-8");
            return feedContent;
        } catch (Exception e) {
            return "An error occurred: " + e.getMessage();
        }
    }

    private JSONObject getDestinationDetails(String destName, JSONObject destSrvCreds, String destSrvToken)
            throws ClientProtocolException, IOException {
        final CloseableHttpClient client = HttpClients.createDefault();
        String uri = destSrvCreds.getString("uri") + "/destination-configuration/v1/destinations/" + destName;
        final HttpGet request = new HttpGet(uri);
        request.setHeader("Authorization", destSrvToken);
        final CloseableHttpResponse response = client.execute(request);
        InputStream inputStream = response.getEntity().getContent();
        String feed = getFeedFromResponse(inputStream);
        return new JSONObject(feed);
    }

    private String getFeedFromResponse(InputStream inputStream) throws IOException {
        byte[] returnedOData = IOUtils.toByteArray(inputStream);
        inputStream.close();
        String feedContent = new String(returnedOData, "UTF-8");
        return feedContent;
    }

    private String getAccessToken(String tokenServiceURL, String clientId, String clientSecret)
            throws URISyntaxException {
        URI xsuaaUrl = new URI(tokenServiceURL);
        UaaContextFactory factory = UaaContextFactory.factory(xsuaaUrl).authorizePath("/oauth/authorize")
                .tokenPath("/oauth/token");
        TokenRequest tokenRequest = factory.tokenRequest();
        tokenRequest.setGrantType(GrantType.CLIENT_CREDENTIALS);
        tokenRequest.setClientId(clientId);
        tokenRequest.setClientSecret(clientSecret);
        UaaContext xsuaaContext = factory.authenticate(tokenRequest);
        CompositeAccessToken accessToken = xsuaaContext.getToken();
        return "Bearer " + accessToken.toString();
    }

}