package com.myvitory.app.data

/**
 * Pontos-chave de execução do catálogo inicial: o que conferir antes de cada série.
 * Ficam no código (não no banco) porque são conteúdo do app, não do usuário.
 */
object ExerciseTips {
    private val tips = mapOf(
        "supino_reto" to listOf(
            "Escápulas juntas e para baixo, pés firmes no chão.",
            "Desça a barra até a linha do peito com os cotovelos a uns 45° do tronco.",
            "Empurre sem tirar o quadril do banco.",
        ),
        "supino_inclinado_halteres" to listOf(
            "Banco entre 30° e 45°.",
            "Desça os halteres ao lado do peito, controlando a descida.",
            "Suba aproximando os halteres sem bater um no outro.",
        ),
        "crucifixo_maquina" to listOf(
            "Ajuste o banco para as mãos ficarem na altura do peito.",
            "Cotovelos levemente flexionados o movimento todo.",
            "Volte devagar até sentir o peito alongar, sem passar da linha do ombro.",
        ),
        "crossover" to listOf(
            "Um pé à frente e tronco levemente inclinado.",
            "Feche os braços em arco, como num abraço, com cotovelos fixos.",
            "Controle a volta; não deixe o peso puxar os braços para trás.",
        ),
        "flexao" to listOf(
            "Corpo reto da cabeça ao calcanhar: abdômen e glúteos contraídos.",
            "Mãos um pouco mais abertas que os ombros.",
            "Desça até o peito quase tocar o chão.",
        ),
        "triceps_polia" to listOf(
            "Cotovelos colados ao corpo e parados.",
            "Estenda até travar o braço, sem jogar o tronco.",
            "Suba só até o antebraço passar da linha do chão.",
        ),
        "triceps_frances" to listOf(
            "Cotovelos apontando para cima, perto da cabeça.",
            "Desça o halter atrás da cabeça com controle.",
            "Não arqueie a lombar: abdômen firme.",
        ),
        "mergulho_paralelas" to listOf(
            "Ombros longe das orelhas durante toda a série.",
            "Desça até o cotovelo chegar perto de 90°.",
            "Tronco mais reto foca no tríceps; inclinado, no peito.",
        ),
        "puxada_frente" to listOf(
            "Pegada um pouco mais aberta que os ombros.",
            "Puxe levando os cotovelos para baixo e para trás, até o queixo.",
            "Não balance o tronco para ajudar.",
        ),
        "remada_curvada" to listOf(
            "Tronco inclinado perto de 45°, coluna neutra.",
            "Puxe a barra em direção ao umbigo, cotovelos perto do corpo.",
            "Joelhos levemente flexionados e firmes.",
        ),
        "remada_baixa" to listOf(
            "Peito aberto e coluna reta, sem jogar o tronco para trás.",
            "Puxe o cabo até a barriga juntando as escápulas.",
            "Estenda os braços devagar na volta.",
        ),
        "remada_unilateral" to listOf(
            "Mão e joelho apoiados no banco, costas paralelas ao chão.",
            "Puxe o halter em direção ao quadril, não ao ombro.",
            "Não gire o tronco para subir mais peso.",
        ),
        "barra_fixa" to listOf(
            "Comece com os braços estendidos e ombros ativos.",
            "Suba até o queixo passar a barra, sem chutar as pernas.",
            "Desça controlando até estender de novo.",
        ),
        "rosca_direta" to listOf(
            "Cotovelos parados ao lado do corpo.",
            "Suba sem balançar o tronco.",
            "Desça até quase estender o braço.",
        ),
        "rosca_alternada" to listOf(
            "Gire o punho para cima enquanto sobe.",
            "Um braço por vez, sem mover o cotovelo.",
            "Desça devagar antes de começar o outro lado.",
        ),
        "rosca_martelo" to listOf(
            "Pegada neutra, palmas viradas uma para a outra.",
            "Cotovelos fixos ao lado do corpo.",
            "Suba até o antebraço passar da linha do chão.",
        ),
        "agachamento_livre" to listOf(
            "Pés na largura dos ombros, pontas levemente para fora.",
            "Desça com o quadril para trás e joelhos na direção dos pés.",
            "Coluna neutra e calcanhares no chão do começo ao fim.",
        ),
        "leg_press_45" to listOf(
            "Pés na largura do quadril, no meio da plataforma.",
            "Desça até o joelho chegar perto de 90°, sem tirar o quadril do encosto.",
            "Não trave o joelho no topo.",
        ),
        "agachamento_hack" to listOf(
            "Costas inteiras apoiadas no encosto.",
            "Desça até a coxa ficar paralela à plataforma.",
            "Empurre pelo meio do pé, sem tirar o calcanhar.",
        ),
        "agachamento_bulgaro" to listOf(
            "Pé de trás apoiado no banco, passo da frente longo.",
            "Desça na vertical até o joelho de trás quase tocar o chão.",
            "Tronco levemente inclinado para frente.",
        ),
        "cadeira_extensora" to listOf(
            "Joelho alinhado com o eixo da máquina.",
            "Estenda as pernas e segure um instante no topo.",
            "Desça devagar, sem deixar o peso bater.",
        ),
        "stiff" to listOf(
            "Joelhos levemente flexionados e parados.",
            "Desça a barra rente às pernas levando o quadril para trás.",
            "Pare quando sentir o posterior alongar, com a coluna ainda reta.",
        ),
        "mesa_flexora" to listOf(
            "Joelho alinhado com o eixo da máquina, quadril colado no banco.",
            "Flexione até o rolo chegar perto do glúteo.",
            "Volte devagar até quase estender.",
        ),
        "levantamento_terra" to listOf(
            "Barra rente à canela, coluna neutra antes de puxar.",
            "Empurre o chão com as pernas e estenda o quadril junto.",
            "Barra perto do corpo do começo ao fim.",
        ),
        "elevacao_pelvica" to listOf(
            "Parte de cima das costas apoiada no banco, barra sobre o quadril.",
            "Suba até o tronco ficar alinhado com as coxas.",
            "Contraia o glúteo no topo, sem arquear a lombar.",
        ),
        "cadeira_abdutora" to listOf(
            "Costas apoiadas e quadril no fundo do banco.",
            "Abra as pernas com controle e segure um instante.",
            "Feche devagar, sem deixar o peso bater.",
        ),
        "panturrilha_em_pe" to listOf(
            "Ponta dos pés na borda, calcanhares livres.",
            "Desça até alongar bem a panturrilha.",
            "Suba o mais alto possível e segure um segundo.",
        ),
        "panturrilha_sentado" to listOf(
            "Apoio sobre a coxa, perto do joelho.",
            "Desça o calcanhar até alongar bem.",
            "Suba até a ponta do pé e segure um segundo.",
        ),
        "panturrilha_leg_press" to listOf(
            "Só a ponta dos pés na borda de baixo da plataforma.",
            "Joelhos estendidos, mas sem travar.",
            "Empurre com a ponta dos pés e volte devagar.",
        ),
        "desenvolvimento_halteres" to listOf(
            "Halteres na linha das orelhas no começo.",
            "Empurre para cima sem arquear a lombar.",
            "Desça até o cotovelo ficar um pouco abaixo do ombro.",
        ),
        "elevacao_lateral" to listOf(
            "Cotovelos levemente flexionados.",
            "Suba os braços até a linha dos ombros, não acima.",
            "Sem balanço: se precisar, diminua o peso.",
        ),
        "crucifixo_invertido" to listOf(
            "Peito apoiado no encosto, braços à frente.",
            "Abra os braços para trás juntando as escápulas.",
            "Movimento curto e controlado, sem encolher os ombros.",
        ),
        "abdominal_polia" to listOf(
            "De joelhos, corda ao lado da cabeça.",
            "Enrole o tronco levando o peito em direção ao quadril.",
            "O quadril fica parado: quem trabalha é o abdômen.",
        ),
        "elevacao_pernas" to listOf(
            "Lombar colada no chão ou no apoio.",
            "Suba as pernas sem usar impulso.",
            "Desça devagar sem encostar os pés no chão.",
        ),
        "supino_reto_halteres" to listOf(
            "Escápulas juntas e pés firmes no chão.",
            "Desça os halteres ao lado do peito, cotovelos a uns 45° do tronco.",
            "Suba aproximando os halteres sem bater um no outro.",
        ),
        "supino_inclinado_barra" to listOf(
            "Banco entre 30° e 45°.",
            "Desça a barra até a parte de cima do peito, perto da clavícula.",
            "Mantenha o quadril no banco e as escápulas encaixadas.",
        ),
        "supino_declinado" to listOf(
            "Prenda bem as pernas antes de tirar a barra.",
            "Desça até a parte de baixo do peito, com controle.",
            "Peça ajuda para tirar e devolver a barra.",
        ),
        "supino_maquina" to listOf(
            "Ajuste o banco para as pegadas ficarem na linha do meio do peito.",
            "Costas apoiadas e escápulas juntas o tempo todo.",
            "Empurre sem travar os cotovelos no final.",
        ),
        "crucifixo_halteres" to listOf(
            "Cotovelos levemente flexionados e fixos do começo ao fim.",
            "Abra os braços em arco até sentir o peito alongar, sem passar da linha do ombro.",
            "Feche como num abraço, sem bater os halteres.",
        ),
        "pullover" to listOf(
            "Apoie a parte de cima das costas no banco, quadril um pouco abaixo.",
            "Leve o halter para trás da cabeça com os cotovelos levemente flexionados.",
            "Volte até acima do peito sem arquear a lombar.",
        ),
        "triceps_corda" to listOf(
            "Cotovelos colados ao corpo; só o antebraço se move.",
            "No fim, abra a corda para os lados e estenda bem os braços.",
            "Suba devagar até os antebraços passarem da linha do cotovelo.",
        ),
        "triceps_testa" to listOf(
            "Cotovelos apontados para o teto, sem abrir para os lados.",
            "Desça a barra até perto da testa, devagar.",
            "Estenda os braços sem mexer os ombros.",
        ),
        "triceps_coice" to listOf(
            "Tronco inclinado à frente e braço colado ao corpo, paralelo ao chão.",
            "Estenda o cotovelo até o braço ficar reto.",
            "Volte devagar sem balançar o ombro.",
        ),
        "supino_fechado" to listOf(
            "Mãos na largura dos ombros, não mais juntas que isso.",
            "Cotovelos perto do tronco durante a descida.",
            "Desça até a parte de baixo do peito e empurre com o tríceps.",
        ),
        "mergulho_banco" to listOf(
            "Mãos no banco na largura do quadril, dedos para a frente.",
            "Desça até os cotovelos ficarem em 90°, com as costas perto do banco.",
            "Pés mais longe deixam mais difícil; comece com os joelhos dobrados.",
        ),
        "puxada_triangulo" to listOf(
            "Tronco um pouco inclinado para trás, peito aberto.",
            "Puxe o triângulo até a parte de cima do peito levando os cotovelos para baixo.",
            "Suba devagar até alongar as costas.",
        ),
        "pulldown_braco_estendido" to listOf(
            "Braços quase retos, cotovelos levemente flexionados.",
            "Puxe a barra em arco até as coxas usando as costas.",
            "Não incline o tronco para ajudar.",
        ),
        "remada_cavalinho" to listOf(
            "Peito apoiado (ou tronco firme) e coluna neutra.",
            "Puxe os pegadores até a barriga juntando as escápulas.",
            "Desça com controle, sem arredondar as costas.",
        ),
        "remada_maquina" to listOf(
            "Ajuste o banco para as pegadas ficarem na linha do peito.",
            "Puxe levando os cotovelos para trás e juntando as escápulas.",
            "Não jogue o tronco para trás para ajudar.",
        ),
        "barra_fixa_supinada" to listOf(
            "Mãos viradas para você, na largura dos ombros.",
            "Suba até o queixo passar da barra, sem balançar.",
            "Desça até estender quase todo o braço.",
        ),
        "hiperextensao" to listOf(
            "Quadril na borda do apoio, braços cruzados no peito.",
            "Suba até o corpo ficar em linha reta, sem passar disso.",
            "Desça devagar mantendo a coluna neutra.",
        ),
        "encolhimento" to listOf(
            "Braços estendidos ao lado do corpo.",
            "Suba os ombros em direção às orelhas, sem girar.",
            "Segure um segundo em cima e desça devagar.",
        ),
        "rosca_scott" to listOf(
            "Axilas encostadas no topo do banco.",
            "Desça até quase estender o braço, sem soltar o peso.",
            "Suba sem tirar os cotovelos do apoio.",
        ),
        "rosca_barra_w" to listOf(
            "Pegue nas curvas da barra W: alivia os punhos.",
            "Cotovelos fixos ao lado do corpo.",
            "Suba sem balançar o tronco e desça devagar.",
        ),
        "rosca_concentrada" to listOf(
            "Sentado, cotovelo apoiado na parte de dentro da coxa.",
            "Suba o halter até o ombro sem mexer o braço.",
            "Desça devagar até quase estender.",
        ),
        "rosca_polia" to listOf(
            "Fique perto da polia, cotovelos colados ao corpo.",
            "Suba a barra até a linha do peito.",
            "A polia mantém a tensão na descida: controle a volta.",
        ),
        "rosca_punho" to listOf(
            "Antebraços apoiados no banco ou nas coxas, punhos para fora.",
            "Deixe a barra rolar até os dedos e feche a mão subindo o punho.",
            "Use carga leve e faça a amplitude completa.",
        ),
        "rosca_punho_inversa" to listOf(
            "Antebraços apoiados, palmas viradas para baixo.",
            "Suba só o punho, sem mexer o cotovelo.",
            "Carga leve e descida lenta.",
        ),
        "agachamento_smith" to listOf(
            "Pés um pouco à frente da barra, na largura dos ombros.",
            "Desça com o tronco firme até as coxas ficarem paralelas ao chão.",
            "Joelhos na direção das pontas dos pés.",
        ),
        "agachamento_frontal" to listOf(
            "Barra apoiada na frente dos ombros, cotovelos altos.",
            "Tronco bem ereto durante a descida.",
            "Desça até onde mantiver os cotovelos altos e os calcanhares no chão.",
        ),
        "agachamento_goblet" to listOf(
            "Segure o halter junto ao peito, cotovelos para baixo.",
            "Desça entre as pernas mantendo o tronco ereto.",
            "Empurre o chão com o pé inteiro para subir.",
        ),
        "afundo" to listOf(
            "Passo longo: os dois joelhos chegam a uns 90° embaixo.",
            "Tronco ereto e joelho da frente alinhado com o pé.",
            "Suba empurrando com o calcanhar da perna da frente.",
        ),
        "subida_banco" to listOf(
            "Banco na altura do joelho, pé inteiro apoiado em cima.",
            "Suba com a força da perna de cima, sem impulsionar com a de baixo.",
            "Desça devagar e controlado.",
        ),
        "cadeira_flexora" to listOf(
            "Joelho alinhado com o eixo da máquina e coxas presas.",
            "Flexione até o fim sem tirar o quadril do banco.",
            "Volte devagar até quase estender.",
        ),
        "stiff_halteres" to listOf(
            "Joelhos levemente flexionados e fixos.",
            "Leve o quadril para trás descendo os halteres rente às pernas.",
            "Desça até sentir o posterior alongar, sem arredondar as costas.",
        ),
        "bom_dia" to listOf(
            "Barra nas costas como no agachamento, joelhos levemente flexionados.",
            "Incline o tronco levando o quadril para trás, coluna neutra.",
            "Use carga leve: é mais técnica que força.",
        ),
        "terra_sumo" to listOf(
            "Pés bem afastados e pontas para fora, mãos por dentro das pernas.",
            "Peito aberto e coluna neutra antes de puxar.",
            "Empurre o chão para os lados e suba estendendo quadril e joelhos juntos.",
        ),
        "cadeira_adutora" to listOf(
            "Costas apoiadas e pernas abertas até onde for confortável.",
            "Feche as pernas com controle, sem bater as almofadas.",
            "Abra devagar; não deixe o peso puxar.",
        ),
        "aducao_polia" to listOf(
            "Caneleira no tornozelo da perna mais perto da polia.",
            "Traga a perna para a frente da outra, cruzando a linha do corpo.",
            "Tronco parado e volta devagar.",
        ),
        "gluteo_polia" to listOf(
            "Caneleira presa no tornozelo, segure firme no aparelho.",
            "Leve a perna para trás contraindo o glúteo, sem arquear a lombar.",
            "Volte devagar sem apoiar o pé.",
        ),
        "gluteo_maquina" to listOf(
            "Ajuste o apoio para o quadril ficar estável.",
            "Empurre para trás com o calcanhar contraindo o glúteo.",
            "Não arqueie a lombar no fim do movimento.",
        ),
        "desenvolvimento_maquina" to listOf(
            "Ajuste o banco para as pegadas ficarem na altura dos ombros.",
            "Costas apoiadas e abdômen firme.",
            "Empurre para cima sem travar os cotovelos.",
        ),
        "desenvolvimento_arnold" to listOf(
            "Comece com os halteres na frente do rosto, palmas para você.",
            "Gire as palmas para a frente enquanto empurra para cima.",
            "Volte girando de novo, devagar.",
        ),
        "elevacao_frontal" to listOf(
            "Braços quase retos, sem balançar o tronco.",
            "Suba até a altura dos ombros.",
            "Desça devagar.",
        ),
        "elevacao_lateral_polia" to listOf(
            "Fique de lado para a polia, com o cabo passando na frente do corpo.",
            "Suba o braço até a altura do ombro, cotovelo levemente flexionado.",
            "A polia segura a tensão: desça devagar.",
        ),
        "crucifixo_invertido_halteres" to listOf(
            "Tronco inclinado à frente, coluna neutra.",
            "Abra os braços para os lados juntando as escápulas.",
            "Carga leve e sem impulso.",
        ),
        "remada_alta" to listOf(
            "Pegada na largura dos ombros ou um pouco menos.",
            "Puxe a barra até a altura do peito, cotovelos acima das mãos.",
            "Se sentir o ombro pinçar, suba menos.",
        ),
        "abdominal_supra" to listOf(
            "Joelhos dobrados e pés no chão.",
            "Tire só as escápulas do chão, sem puxar o pescoço.",
            "Solte o ar subindo e desça devagar.",
        ),
        "abdominal_infra" to listOf(
            "Lombar colada no chão o tempo todo.",
            "Leve os joelhos em direção ao peito tirando o quadril do chão.",
            "Desça devagar, sem deixar as pernas caírem.",
        ),
        "abdominal_obliquo" to listOf(
            "Deitado, leve o cotovelo em direção ao joelho oposto.",
            "Gire o tronco, não só o braço.",
            "Alterne os lados com controle.",
        ),
        "abdominal_maquina" to listOf(
            "Ajuste o banco para as almofadas ficarem no peito.",
            "Enrole o tronco para a frente contraindo o abdômen.",
            "Volte devagar, sem deixar o peso bater.",
        ),
        "prancha" to listOf(
            "Cotovelos embaixo dos ombros, corpo reto da cabeça ao calcanhar.",
            "Contraia abdômen e glúteos; não deixe o quadril cair.",
            "Respire normalmente e conte o tempo.",
        ),
    )

    fun forExercise(id: String): List<String> = tips[id].orEmpty()
}
