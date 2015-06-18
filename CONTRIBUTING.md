#Contributing to squbs

We love your contributions. Please use the steps below to determine the path of contribution.

##Discussions/Proposals
We all should discuss new ideas before they turn into code. The [squbs-user forum](https://groups.google.com/forum/#!forum/squbs-user) is a great starting point for such discussions. Once we get to a certain conclusion, please file an issue in the [issue tracker](https://github.com/paypal/squbs/issues).

##Bugs
If you have determined you are facing a bug or defect, please log the bug in the [issue tracker](https://github.com/paypal/squbs/issues).

##Contribution Process
The standard way of contributing ideas/features and bug fixes is by pull requests.

* Make sure you have an active github account.
* [Fork](https://help.github.com/articles/fork-a-repo/) the squbs repo into your account.
* Make modifications to the master branch. If the contribution is a bug fix that needs to go into a release branch, please provide that as a comment on the pull request (below).
* Provide test cases and ensure the test coverage of the added code and functionality.
* Commit and push your contributions.
* Please [squash](https://github.com/edx/edx-platform/wiki/How-to-Rebase-a-Pull-Request) multiple commits for a single feature and bug fix into a single commit.
* Make a [pull request](https://help.github.com/articles/using-pull-requests/). For bug fixes that need to go into a current release, please note so in the pull request comments.
* We'll review your pull requests and use github to communicate comments. Please ensure your email account in github is accurate as it will be used to communicate review comments.
* If modifications are required, please [squash](https://github.com/edx/edx-platform/wiki/How-to-Rebase-a-Pull-Request) your commits after making the modifications to update the pull request.
* We merge the pull request after successful submission and review, and close the issue. In case of bug fixes that need to go into a current release branch, we'll do the proper cherry-pick.
* Upon successful merge and regression tests, the SNAPSHOT artifact reflecting the change will be published on [maven central snapshot repository](https://oss.sonatype.org/content/repositories/snapshots/).

Thank you very much in advance!