#!/bin/bash

# ============================================
# Script de test du pipeline JONK CI/CD
# ============================================

set -e

echo "╔═══════════════════════════════════════════════════════════╗"
echo "║       JONK CI/CD Engine - Script de Test                 ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""

# Configuration
API_URL="http://localhost:8080/api/pipeline"
REQUEST_FILE="${1:-examples/request-simple.json}"

# Couleurs
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Fonction pour afficher les messages
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# 1. Vérifier que l'API est accessible
echo ""
log_info "Test 1: Vérification de la santé de l'API..."
HEALTH_RESPONSE=$(curl -s "$API_URL/health")

if echo "$HEALTH_RESPONSE" | grep -q "UP"; then
    log_success "API accessible et opérationnelle"
    echo "$HEALTH_RESPONSE" | jq '.'
else
    log_error "API non accessible. Assurez-vous que JONK est démarré."
    exit 1
fi

# 2. Vérifier le fichier de requête
echo ""
log_info "Test 2: Vérification du fichier de requête..."
if [ ! -f "$REQUEST_FILE" ]; then
    log_error "Fichier de requête non trouvé: $REQUEST_FILE"
    log_info "Utilisation: $0 [chemin_vers_request.json]"
    exit 1
fi
log_success "Fichier de requête trouvé: $REQUEST_FILE"
echo ""
echo "📋 Contenu de la requête:"
cat "$REQUEST_FILE" | jq '.'

# 3. Lancer le pipeline
echo ""
log_info "Test 3: Lancement du pipeline..."
PIPELINE_RESPONSE=$(curl -s -X POST "$API_URL/run" \
    -H "Content-Type: application/json" \
    -d @"$REQUEST_FILE")

# Vérifier la réponse
if echo "$PIPELINE_RESPONSE" | grep -q "executionId"; then
    log_success "Pipeline lancé avec succès"
    EXECUTION_ID=$(echo "$PIPELINE_RESPONSE" | jq -r '.executionId')
    echo ""
    echo "📦 Réponse:"
    echo "$PIPELINE_RESPONSE" | jq '.'
    echo ""
    log_info "Execution ID: $EXECUTION_ID"
else
    log_error "Erreur lors du lancement du pipeline"
    echo "$PIPELINE_RESPONSE" | jq '.'
    exit 1
fi

# 4. Suivre l'exécution
echo ""
log_info "Test 4: Suivi de l'exécution du pipeline..."
log_warning "Cette étape peut prendre plusieurs minutes selon le projet..."
echo ""

MAX_ATTEMPTS=60
ATTEMPT=0
COMPLETED=false

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    ATTEMPT=$((ATTEMPT + 1))
    
    # Récupérer le statut
    STATUS_RESPONSE=$(curl -s "$API_URL/$EXECUTION_ID")
    CURRENT_STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status')
    
    echo -ne "\r⏳ Tentative $ATTEMPT/$MAX_ATTEMPTS - Statut: $CURRENT_STATUS      "
    
    # Vérifier si terminé
    if [ "$CURRENT_STATUS" = "SUCCESS" ]; then
        echo ""
        log_success "Pipeline terminé avec SUCCÈS !"
        COMPLETED=true
        break
    elif [ "$CURRENT_STATUS" = "FAILED" ]; then
        echo ""
        log_error "Pipeline ÉCHOUÉ"
        COMPLETED=true
        break
    elif [ "$CURRENT_STATUS" = "ROLLED_BACK" ]; then
        echo ""
        log_warning "Pipeline rollbacké"
        COMPLETED=true
        break
    fi
    
    sleep 5
done

echo ""

if [ "$COMPLETED" = false ]; then
    log_warning "Timeout atteint. Le pipeline est toujours en cours..."
    log_info "Vous pouvez consulter le statut manuellement:"
    echo "   curl $API_URL/$EXECUTION_ID | jq '.'"
fi

# 5. Afficher les détails
echo ""
log_info "Test 5: Récupération des détails du pipeline..."
DETAILS=$(curl -s "$API_URL/$EXECUTION_ID")
echo "$DETAILS" | jq '.'

# Statistiques
TOTAL_STEPS=$(echo "$DETAILS" | jq -r '.totalSteps // 0')
SUCCESS_STEPS=$(echo "$DETAILS" | jq -r '.successSteps // 0')
FAILED_STEPS=$(echo "$DETAILS" | jq -r '.failedSteps // 0')
DURATION=$(echo "$DETAILS" | jq -r '.durationMs // 0')

echo ""
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║                 RÉSUMÉ DE L'EXÉCUTION                      ║"
echo "╠═══════════════════════════════════════════════════════════╣"
echo "║ Execution ID    : $EXECUTION_ID"
echo "║ Statut final    : $CURRENT_STATUS"
echo "║ Total étapes    : $TOTAL_STEPS"
echo "║ Étapes réussies : $SUCCESS_STEPS"
echo "║ Étapes échouées : $FAILED_STEPS"
echo "║ Durée           : $((DURATION / 1000))s ($DURATION ms)"
echo "╚═══════════════════════════════════════════════════════════╝"

# 6. Afficher les logs (optionnel)
echo ""
read -p "Voulez-vous afficher les logs détaillés ? (y/N) " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    log_info "Récupération des logs..."
    LOGS_RESPONSE=$(curl -s "$API_URL/$EXECUTION_ID/logs")
    echo "$LOGS_RESPONSE" | jq -r '.logs[]'
fi

# 7. Lister toutes les exécutions
echo ""
log_info "Test 6: Liste des exécutions récentes..."
EXECUTIONS=$(curl -s "$API_URL/executions")
echo "$EXECUTIONS" | jq '.[:5]'

echo ""
log_success "Tests terminés !"
echo ""

# Commandes utiles
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║              COMMANDES UTILES                              ║"
echo "╠═══════════════════════════════════════════════════════════╣"
echo "║ Consulter le pipeline:                                     ║"
echo "║   curl $API_URL/$EXECUTION_ID | jq '.'"
echo "║                                                             ║"
echo "║ Voir les logs:                                             ║"
echo "║   curl $API_URL/$EXECUTION_ID/logs | jq -r '.logs[]'"
echo "║                                                             ║"
echo "║ Lister les exécutions:                                     ║"
echo "║   curl $API_URL/executions | jq '.'"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""

exit 0
