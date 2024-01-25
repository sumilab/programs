This program is an implementation of GSA in [COINS](https://sourceforge.net/projects/coins-project/) compiler.

Example of compilation
`java -classpath ./classes coins.driver.Driver -coins:target=x86_64,ssa-opt=esplt/divex3/gsa`

* `esplt`: critical edge elimination
* `divex3`: making each statement three-address code
* `gsa`: applying GSA

# Publication
1. Tomohiro Sano and Yasunobu Sumikawa, [Global Store Statement Aggregation](https://sumilab.github.io/web/pdf/2023/paap_2023.pdf), In Proceedings of the 14th International Symposium on Parallel Architectures, Algorithms and Programming, PAAP'23, pp.1-6, IEEE, 2023.
