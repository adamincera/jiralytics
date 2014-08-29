# jira-scraper

A Clojure library designed to fetch and interpret data from the JIRA REST API.

## Structure

This application has two namespaces: `jiralytics.core` and `jiralytics.handler`.

### Jiralytics.core

This namespace contains the library functions that the end user can access through the web application. The full list of functions and their descriptions can be found by looking at the source code.

### Jiralytics.handler

This namespace contains the handler for the web application. Most of the legwork in this app is done by `parse-expression`, which takes a URI and, if the URI is formatted correctly, returns a string representation of a valid Clojure expression. 

There are only a few routes:

    1. The home page: Simply prints "Welcome to the JIRA Analytics Web Application!"
    2. The expression parser: passes part of the URI to `parse-expression` and evaluates 
       the resulting expression. More details in the **Usage** section.
    3. "Not Found" catches anything else.

## Credentials

As the handler is currently written, it simply `slurp`s the username and password from a plaintext file containing {:username <your_username> :password <your_password>}. This is simply to avoid hard-coding my credentials into the handler, and will be changed in future versions.


## Usage

Functions in the jira-scraper library are meant to be executed on a collection of zero or more issues. All functions are formatted so that they take parameters as `[issues param1 param2 ....]`, where `issues` is the set of issues the function is being called on and `param`s are whatever other params the function is designed to take. There are functions which take no additional parameters, so they simply take `[issues]`. All functions are also written in the context of handling issues which all pertain to the same project.

In addition, the expression-parsing route is defined as:
 
`GET "/project/:project*"`

Bearing this in mind, `parse-expression` can interpret a URI containing an arbitrary number of functions, each with an arbitrary number of arguments. However, it must be formatted in the following way:

    http://host.name.com/project/<project name>/f/function1/arg1/arg2/.../f/function2/arg1/arg2/...

Each function name must be preceded by `/f/` and followed by whatever extra arguments are to be passed to it. Additionally, functions must be listed in the order they are to be executed, from the inside out. So,

    http://host.name.com/project/myProject/f/function1/arg1/arg2/f/function2/f/function3/arg3

would be parsed into 

    "(function3 (function 2 (function1 (get-project "myProject" "myUserName" "myPassword") arg1 arg2)) arg3)"


## Running

To start a web server for the application, run:

    lein ring server

