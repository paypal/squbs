# Contributing to squbs

We love your contributions. Please use the steps below to determine the path of contribution.

## Discussions/Proposals
We all should discuss new ideas before they turn into code. The [squbs gitter chat](https://gitter.im/paypal/squbs?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) is a great starting point for such discussions. Once we get to a certain conclusion, please file an issue in the [issue tracker](https://github.com/paypal/squbs/issues) with reference to the forum discussion.

## Bugs
If you have determined you are facing a bug or defect, please log the bug in the [issue tracker](https://github.com/paypal/squbs/issues). If the bug has any reference to forum discussions, please add the reference to the forum discussion.

## Contribution Process
The standard way of contributing ideas/features and bug fixes is by pull requests.

* Make sure you have an active github account.
* [Fork](https://help.github.com/articles/fork-a-repo/) the squbs repo into your account.
* Make modifications to the master branch. If the contribution is a bug fix that needs to go into a release branch, please provide that as a comment on the pull request (below).
* Provide test cases and ensure the test coverage of the added code and functionality.
* If the change impacts documentation, please provide documentation fixes or additional documentation for the feature.
* Commit and push your contributions. The commit message must reference the issue solved by this commit.
* Please [squash](https://github.com/edx/edx-platform/wiki/How-to-Rebase-a-Pull-Request) multiple commits for a single feature and bug fix into a single commit.
* Make a [pull request](https://help.github.com/articles/using-pull-requests/). For bug fixes that need to go into a current release, please note so in the pull request comments.
* We'll review your pull requests and use github to communicate comments. Please ensure your email account in github is accurate as it will be used to communicate review comments.
* If modifications are required, please [squash](https://github.com/edx/edx-platform/wiki/How-to-Rebase-a-Pull-Request) your commits after making the modifications to update the pull request.
* We merge the pull request after successful submission and review, and close the issue. In case of bug fixes that need to go into a current release branch, we'll do the proper cherry-pick.
* Upon successful merge and regression tests, the SNAPSHOT artifact reflecting the change will be published on [maven central snapshot repository](https://oss.sonatype.org/content/repositories/snapshots/).

## Traceability

We place great value on traceability of all changes. All features must have use cases and have been discussed publicly
in the forum. Issues filed for such features **MUST** have a reference to the forum discussion in form of the discussion
URL or the issue will not be accepted. Similarly, every commit **MUST** have a reference to the issue it resolves. This
can be addressed either in form of the issue id or a URL to the issue. Pull requests with commit messages not
referencing the issue will not be merged.

Generally, it is a good practice to reflect a single issue in a pull request. Multiple issues fixed by a single
pull request will be accepted **only if** these cannot be separated into individual commits and individual pull requests
reflecting each issue separately.

Thank you very much in advance!
