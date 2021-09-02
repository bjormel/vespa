// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: mpolden
package cmd

import (
	"fmt"
	"log"
	"os"
)

var exitFunc = os.Exit // To allow overriding Exit in tests

func printErrHint(err error, hints ...string) {
	printErr(nil, err.Error())
	for _, hint := range hints {
		log.Print(color.Cyan("Hint: "), hint)
	}
	exitFunc(1)
}

func printErr(err error, msg ...interface{}) {
	if len(msg) > 0 {
		log.Print(color.Red("Error: "), fmt.Sprint(msg...))
	}
	if err != nil {
		log.Print(color.Yellow(err))
	}
	exitFunc(1)
}

func printSuccess(msg ...interface{}) {
	log.Print(color.Green("Success: "), fmt.Sprint(msg...))
}
