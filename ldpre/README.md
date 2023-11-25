This program is an implementation of LDPRE in [COINS](https://sourceforge.net/projects/coins-project/) compiler.

Example of compilation
`java -classpath ./classes coins.driver.Driver -coins:target=x86_64,ssa-opt=esplt/divex/prun/ldpre/srd3`

* `esplt`: critical edge elimination
* `divex`: making each statement three-address code
* `prun`: converting program from the normal form to the SSA form
* `ldpre`: applying LDPRE
* `srd3`: converting program from the SSA form to the normal form

# Publication
1. Yuya Yanase and Yasunobu Sumikawa, [Lazy Demand-Driven Partial Redundancy Elimination](https://sumilab.github.io/web/pdf/2023/jip_ldpre.pdf), Journal of Information Processing, Vol. 31, pp. 459–468, 2023.