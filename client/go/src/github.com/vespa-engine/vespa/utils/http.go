// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package utils

import (
    "bufio"
    "net/http"
    "strings"
    "time"
)

var httpClient = CreateClient()

type HttpClient interface {
    get(url string) (response *http.Response, error error)
}

type defaultHttpClient struct {
    client http.Client
}

func (c defaultHttpClient) get(url string) (response *http.Response, error error) {
    return c.client.Get(url)
}

func CreateClient() (client HttpClient) {
    return &defaultHttpClient{
        client: http.Client{Timeout: time.Second * 10,},
    }
}

func HttpRequest(host string, path string, description string) (response *http.Response) {
    response, error := httpClient.get(host + path)
    if error != nil {
        Error("Could not connect to", strings.ToLower(description), "at", host)
        Detail(error.Error())
        return
    }
    defer response.Body.Close()

    scanner := bufio.NewScanner(response.Body)

    if error := scanner.Err(); error != nil {
        Error("Error reading data from", strings.ToLower(description), "at", host)
        Detail(error.Error())
        return nil
    } else {
        return response
    }
}
